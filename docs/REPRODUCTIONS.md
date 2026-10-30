# Подтверждённые ошибки старой реализации

Номера мобов (`#644`, `#776`) — временные Minecraft entity ID одного запуска; при новых запусках будут другими. Полные примеры содержатся в фильтрованных файлах `logs/`. Эти признаки воспроизводятся на разных альфах, но не являются уже проверенными тестами новой системы.

## alpha3.24 — гризли #644 (27.10.2026 01:11–01:12)

1. `01:11:46.555`: `IDLE → ASSESS`, `human_detected_hold_ground`, дистанция 12.19 блоков. HP гризли 40/40, HP игрока 20/20, `ownFear=0`, `huntDrive≈39.78`, `satiety≈60.30`.
2. `01:11:46.777`: после `SpeciesBehavior2026` `ASSESS → DEFEND`, причина `individual_hunger_aggression`, около 10.9 блока; реального страха нет.
3. `01:11:52.641`: `ASSESS → RETREAT` по `player_pursuing`, дистанция 15.18; `escapeScore=59.24` vs `attackScore=40.11`, `ownFear=0`.
4. `01:12:02.747`: `ATTACK → DEFEND` по `species_close_defense`, дистанция 1.91 блока. Страх 0, HP 100%.
5. `01:12:03.930`: `DEFEND → RETREAT` по `human_too_close` (серверный tick 508071). `01:12:03.967`: другой модуль возвращает `RETREAT → ATTACK` (`grizzly_hold_defensive_engagement`) на следующем тике. Повторяется 01:12:06.999 → 01:12:07.040, 01:12:11.368 → 01:12:11.411 и далее.
6. `01:12:08.791`: `BLOCK ATTACK->RETREAT reason=player_pursuing`, но в state trace остаётся `intent=ATTACK reason=player_pursuing` — **заблокированное предложение изменило причину**.
7. `01:11:55.423` и `01:11:55.467`: две точки отхода (RETREAT) за ~44 мс, разворот навигации на `153.26°`. Это настоящая проблема смены направления маршрута.

**Важно:** не утверждать, что `FLEE` сработал у этого гризли. Все наблюдавшиеся подробные записи имели `ownFear=0` и `forcedFleeUntil=0`. `RETREAT` был вызван обычным pursuit/proximity-правилом, а не паникой.

## alpha3.23 — гризли #776 (26.10.2026 21:50)

- `21:50:05.728`: `ATTACK → RETREAT`, причина `player_pursuing`.
- `21:50:05.815`: `RETREAT → ATTACK`, причина `chase_reflex_fleeing_player` (~87 мс).
- Стая гиен перестала массово нападать из-за голода, но в этом тесте атака на игрока с низким HP не подтверждена.

## alpha3.21 — гиены и гризли (26.10.2026 18:28–18:40)

- Гиены #241, #237, #240 получали `ATTACK` из `starving_hunt_player` при `huntDrive≈98–100`, порог 72 и дистанция 58–60 блоков. Позднее #241 также атакует по этой причине на 76.2 блока.
- Гризли #418 получал `ATTACK` по `starving_hunt_player` при `huntDrive≈99.2`, порог 78, дистанция 66.1 блока.
- У гиены #242 — многократный цикл `ATTACK ↔ DEFEND` из-за `starving_hunt_player` и `defensive_pack_call` с интервалами ~40–50 мс.
- У гризли #1709 — `ATTACK ↔ RETREAT` из-за `player_pursuing`, `human_too_close`, `chase_reflex_fleeing_player`.
- `FleeRouteGuard.getPosAway` в alpha3.21 возвращал ванильный `DefaultRandomPos.getPosAway` для `horizontal<18`; при `RETREAT` запрашивалось 10, поэтому route-lock не стабилизировал RETREAT.

## Ещё проблема

На прежней alpha3.23 гиены перемещались между `ASSESS→RELOCATE` и `RELOCATE→ASSESS` по `heard_player_relocate` / `confident_pack_hold_player_ratio`. Блокировки должны быть логически объяснимы и не нарушать стабильность движения.
