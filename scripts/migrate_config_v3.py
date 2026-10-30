#!/usr/bin/env python3
"""Перенос пользовательских настроек v3 в отдельный конфиг v4 без изменения исходного файла."""
import argparse
import json
import math
from pathlib import Path
import tomllib

SPECIES = ('grizzly_bear','black_bear','polar_bear','wolf','direwolf','hyena','cougar','panther','lion','sabertooth','tiger','crocodile')
# v3 default, новое поле, виды, допустимый диапазон v4.
RULES = {
    'hyenaPackPlayerHp': (.7,'playerHpHuntThreshold',('hyena',),.1,1),
    'hyenaSoloHunger': (.8,'hungerHuntThreshold',('hyena',),0,1),
    'hyenaSoloChance': (.02,'soloHuntChance',('hyena',),0,.3),
    'adultBearChaseMultiplier': (2.,'chaseSpeed',SPECIES[:3],.1,3),
    'adultBearFleeMultiplier': (1.25,'fleeSpeed',SPECIES[:3],.1,3),
    'criticalHp': (.25,'criticalHp',SPECIES,.01,.5),
    'panicEnter': (.8,'fearOverrideThreshold',SPECIES,.1,1),
    'panicHoldTicks': (80,'panicHoldTicks',SPECIES,20,1200),
    'combatHoldTicks': (100,'combatHoldTicks',SPECIES,100,1200),
    'retreatHoldTicks': (40,'minimumPhaseTicks',SPECIES,10,200),
    'memoryTicks': (160,'visionMemoryTicks',SPECIES,10,2400),
    'soloEncounterCooldownTicks': (1200,'encounterCooldownTicks',('hyena',),100,12000),
    'packGraceTicks': (100,'packBreakDelayTicks',('wolf','direwolf','hyena'),10,600),
    'packEnterRadius': (18.,'packJoinRadius',('wolf','direwolf','hyena'),2,128),
    'packExitRadius': (24.,'packBreakRadius',('wolf','direwolf','hyena'),2,160),
    'repathTicks': (12,'repathTicks',SPECIES,5,200),
    'stuckTicks': (60,'stuckTicks',SPECIES,10,1200),
}

def convert(old, include_defaults=False):
    result = {}
    notes = []
    for key, value in old.get('behavior', {}).items():
        if key not in RULES:
            notes.append(f'{key}: оставлен только в исходном файле; прямого соответствия v4 нет')
            continue
        default, target, species, low, high = RULES[key]
        if value == default and not include_defaults:
            continue
        if not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
            raise ValueError(f'{key}={value}: несовместимо с v4 ({low}..{high}); исходный файл сохранён')
        if target.endswith('Ticks') and int(value) != value:
            raise ValueError(f'{key}: требуется целое число тиков')
        for name in species:
            result.setdefault('tfc:'+name, {})[target] = value
    lines = ['# Перенесены переопределения v3; отсутствующие значения заполнит NeoForge v4.', '[diagnostics]', 'enabled = true', '']
    for species, settings in sorted(result.items()):
        if settings.get('packBreakRadius',30)<settings.get('packJoinRadius',20):
            raise ValueError(f'{species}: packBreakRadius < packJoinRadius')
        lines.append('[species.'+json.dumps(species)+']')
        lines.extend(f'{key} = {value}' for key,value in settings.items())
        lines.append('')
    notes.append('panicExit не переносится: v4 использует оценку опасности и отдельное восстановление; оригинал сохранён')
    notes.append('debug v3 не переносится: автоматическая диагностика alpha включена согласно ТЗ v4')
    return '\n'.join(lines), notes

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('old',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--include-defaults',action='store_true',help='Перенести также стандартные числа v3 вместо предложенных defaults v4')
    args=parser.parse_args()
    text,notes=convert(tomllib.loads(args.old.read_text(encoding='utf-8')),args.include_defaults)
    # Режим x предотвращает незаметную перезапись готового нового конфига.
    with args.output.open('x',encoding='utf-8') as f:f.write(text)
    print('\n'.join(notes));print('Создан: '+str(args.output))
if __name__=='__main__':main()
