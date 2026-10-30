#!/usr/bin/env python3
"""Разбор только JSON после префикса; содержимое журнала никогда не исполняется."""
import argparse
import collections
import csv
import json
import re
from pathlib import Path

PREFIX = '[TFC-WILDLIFE-TRACE] '


def analyze(lines, csv_file=None):
    counts = collections.Counter()
    encounters = collections.defaultdict(collections.Counter)
    issues = []
    previous = {}
    physical = {}
    routes = {}
    sequence = None
    rows = csv.writer(csv_file, lineterminator="\n") if csv_file else None
    if rows:
        rows.writerow(['tick', 'seq', 'animal', 'encounter', 'stage', 'phase', 'reason'])
    total = 0
    for number, line in enumerate(lines, 1):
        if PREFIX not in line:
            continue
        try:
            e = json.loads(line.split(PREFIX, 1)[1])
            tick, seq, mob = e['serverTick'], e['eventSequence'], e['animalUUID']
            stage, reason = e['stage'], e['reason']
            if not all(isinstance(v, str) for v in (mob, stage, reason)) or not isinstance(e.get('data', {}), dict):
                raise ValueError('invalid trace schema')
            if not isinstance(tick, int) or not isinstance(seq, int):
                raise ValueError('tick/seq are not integers')
        except (ValueError, KeyError, TypeError):
            issues.append((number, 'INVALID_OR_TRUNCATED_JSON'))
            continue
        total += 1
        if sequence is not None and seq <= sequence:
            issues.append((number, 'SEQUENCE_REVERSED_OR_SESSION_RESTART'))
        elif sequence is not None and seq != sequence + 1:
            issues.append((number, 'MISSING_SEQUENCE'))
        sequence = seq
        counts[stage] += 1
        key = (mob, e.get('encounterId', 'none'))
        encounters[key][stage] += 1
        data = e.get('data') or {}
        phase = ''
        if stage == 'ARBITRATION':
            a = data.get('arbitration', {})
            before = a.get('avant', {})
            after = a.get('decision', {})
            view = data.get('perception', {})
            phase = after.get('phase', '')
            if before.get('phase') in ('ATTACK', 'HUNT') and phase in ('WARN', 'ASSESS', 'RETREAT', 'DRIVE_OFF'):
                issues.append((number, 'COMBAT_MICRO_REVERSAL'))
            if phase in ('ATTACK', 'HUNT') and after.get('motif') in ('HUNGER_HUNT', 'PACK_HUNT') and e.get('species') == 'tfc:hyena' and view.get('groupe', {}).get('joueurs', 0) >= 2:
                issues.append((number, 'HYENA_MULTIPLAYER_HUNT'))
            if before.get('phase') == phase and before.get('cible') == after.get('cible') and before.get('raison') != after.get('raison'):
                issues.append((number, 'REASON_CHANGED_WITHOUT_TRANSITION'))
            last = previous.get(mob)
            if last and tick == last[0]:
                issues.append((number, 'MULTIPLE_ARBITRATIONS_PER_TICK'))
            if last and phase != last[1]:
                encounters[key]['transitions'] += 1
            previous[mob] = (tick, phase)
            for rejected in a.get('refus', []):
                counts['VETO:' + str(rejected.get('veto', 'missing'))] += 1
            counts['PACK:' + str(view.get('groupe', {}).get('veto', 'none'))] += 1
        elif stage == 'COMBAT' and data.get('phase') == 'DRIVE_OFF':
            issues.append((number, 'DRIVE_OFF_MELEE'))
        elif stage == 'PHYSICAL':
            yaw = data.get('yaw', 0)
            last = physical.get(mob)
            if last and tick - last[0] <= 2 and abs((yaw - last[1] + 180) % 360 - 180) > 90.5:
                issues.append((number, 'TURN_OVER_90_REQUIRES_EVENT_REVIEW'))
            physical[mob] = (tick, yaw)
        elif stage == 'NAV' and reason == 'move_accepted':
            last = routes.get(mob)
            if last and tick - last <= 1:
                issues.append((number, 'CONSECUTIVE_ROUTE_REQUESTS'))
            routes[mob] = tick
        if reason == 'DROPPED_EVENTS':
            counts['dropped'] += data.get('count', 0)
            issues.append((number, 'TRACE_INCOMPLETE_DROPPED_EVENTS'))
        if reason == 'trace_radius_limit':
            issues.append((number, 'TRACE_INCOMPLETE_RADIUS_LIMIT'))
        if rows:
            # Вывод содержит только разрешённые поля. Произвольный текст игры не экспортируется.
            safe = [tick, seq, mob, key[1], stage, phase, reason]
            cells = [re.sub(r'[^A-Za-z0-9_.:#-]', '?', str(v))[:180] for v in safe]
            rows.writerow(["'"+v if v.startswith(('=', '+', '-', '@')) else v for v in cells])
    return {'events': total, 'counts': dict(counts), 'encounters': encounters, 'issues': issues}


def markdown(result):
    out = ['# Анализ Wildlife v4', '', f"Событий: {result['events']}; встреч: {len(result['encounters'])}; диагностических находок: {len(result['issues'])}.",
           '', 'Находка — повод проверить соседние события, а не автоматическое доказательство ошибки ИИ.', '', '| Событие / veto | Количество |', '|---|---:|']
    out += [f'| {k} | {v} |' for k, v in sorted(result['counts'].items())]
    out += ['', '| Животное | Встреча | Переходы | Решения |', '|---|---|---:|---:|']
    out += [f"| {mob} | {enc} | {counts['transitions']} | {counts['ARBITRATION']} |" for (mob, enc), counts in result['encounters'].items()]
    out += ['', '| Строка | Находка |', '|---:|---|']
    out += [f'| {line} | {code} |' for line, code in result['issues']]
    if not result['events']:
        out += ['', 'Нет строк v4. Старые журналы требуют отдельного анализа; отсутствие событий не означает отсутствие багов.']
    return '\n'.join(out) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--output', type=Path, default=Path('trace-report'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with args.log.open(encoding='utf-8', errors='replace') as lines, (args.output/'events.csv').open('w', newline='', encoding='utf-8') as csv_out:
        result = analyze(lines, csv_out)
    (args.output/'report.md').write_text(markdown(result), encoding='utf-8')
    print(f"events={result['events']} findings={len(result['issues'])} report={args.output/'report.md'}")

if __name__ == '__main__':
    main()
