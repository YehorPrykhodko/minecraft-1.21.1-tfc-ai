# Аудит до переписывания

Проверены `AGENTS.md`, задание, все документы, 16 сценариев acceptance,
контрольные суммы всего стартового пакета, сигнатуры и байткод alpha3.24
(`javap -p -c -constants`), а также отфильтрованные логи трёх версий.
JAR используется только как наблюдаемый образец; его классы не включаются в сборку.

## Установлено по байткоду и журналам

* `TFCWildlifeAI.onEntityTickPost`: decay → `SpeciesBehavior2026.observe` →
  `evaluateSituation` → `applyIntent`; внутри последнего вызывается
  `Alpha34Runtime.beforeApplyIntent`. Затем отдельно обновляются social/vision.
  В одном цикле смешиваются наблюдения разного возраста и несколько авторов решений.
* `WildlifeState.setIntent` вызывает `CombatPriorityGuard.skip`, но и при повторном
  принятии того же намерения обновляет `intentSince`, `intentUntil`, `decisionReason`.
  Для RETREAT/FLEE/RELOCATE сбрасывает `nextPathAt=0`. Сам guard не объясняет все
  записи причины: другие модули работают с состоянием через reflection.
* `HyenaRules.humanHunt`: при >=2 взрослых проверяет **HP игрока <0.7**, собственные
  HP >0.4 и отсутствие forced flee. Соло возвращает исходную eligibility.
  `encounterChance` в стае возвращает 1/0 по HP игрока; для соло требует
  huntDrive >=72, затем использует вероятность 0.30–0.62. Последняя величина
  не соответствует требованию редкой охоты и не переносится как правильный баланс.
* `PredatorBalancePatch.bearActiveFactor`: ATTACK/CHASE ×2; FLEE возвращается
  раньше через `adultEarlyFactor` ×1.25; HUNT ×1.0. У детёнышей
  `cubSpeedFactor`: обычный ×0.85, FLEE ×1.105 (=0.85×1.30). Волки ×1.2.
  `walkingRetreatSpeed` ничего не меняет. Коэффициенты относятся к конкретному
  старому способу изменения атрибута, а не автоматически к navigation speed.
* Сохраняются плоские persistent-data ключи `tfc_wildlife_ai.version`,
  `tfc_wildlife_ai.satiety`, `tfc_wildlife_ai.stamina`; диапазон ресурсов 0–100.
* Интеграции: server EntityTick Post, VanillaGameEvent (шаги/бег/приземление),
  TaCZ `com.tacz.guns.api.event.common.GunFireEvent`, ExplosionEvent.Detonate,
  damage/death/join, административные команды. Есть LOS/FOV, затухание слуха,
  личность по UUID, голод, выносливость, социальная защита и забота о детёнышах.
* Старые mixin: `PredatorAi.getAttackTarget` фильтрует выбор добычи;
  `PackPredatorAi.getAttackTarget` ограничивает наследование цели;
  `Predator.hurt` добавляет реакцию после урона, а redirects убирают дубли звуков;
  relocate cooldown mixin относится к внутреннему WildlifeState старого мода.
  Эти классы и guards не переносятся в новую реализацию.

## Воспроизведения

* alpha3.24 grizzly #644: tick 508071 DEFEND→RETREAT `human_too_close`, затем
  восстановление атаки. Во всех относящихся к эпизоду подробных записях fear=0,
  forcedFleeUntil=0. Это **не доказательство ошибочной паники FLEE**.
  Есть отвергнутый ATTACK→RETREAT с оставшейся причиной `player_pursuing` и
  смена направления отхода на 153.26° за ~44 мс.
* alpha3.23 #776: ATTACK→RETREAT→ATTACK за ~87 мс.
* alpha3.21 #1709: аналогичный pursuit/proximity/chase цикл; у гиен
  голодная охота и defensive pack call конкурируют каждые 40–50 мс.
* Это записи старой игры, а не результаты тестирования новой реализации.

## Источники совместимости и неопределённости

Исходники официального TFC **v4.2.7**, commit
`910b508916e10421944b7cdf3578a62879ee462c`, проверены отдельно.
`Predator.customServerAiStep` единолично запускает brain; PackPredator
(`common.entities.ai.predator`) и FelinePredator наследуют этот метод.
`Prey` и `RammingPrey` имеют собственные реализации. Отмена только выбора цели
недостаточна: MoveToTargetSink/FIGHT/AVOID всё ещё могли бы двигать животное.

Переданный пользователем TFC v18 превышает лимит инструмента загрузки 32 МиБ.
Его бинарное содержимое **не было доступно**. Нельзя утверждать совместимость
его походки, custom mixin и сна только на основании исходников официальной версии.
TaCZ hotfix-r3 также не предоставлен. Публичный GunFireEvent может означать
попытку выстрела; предпочтителен подтверждённый GunFirePostEvent, если он есть.

Новые значения страха/длительностей/редкой вероятности — явно настраиваемые
начальные предположения, не восстановленные точные формулы старой игры.
Внешние моды, также меняющие brain/цели/navigation, требуют отдельной проверки.
Приручение, размножение, океанские существа, домашние питомцы и скот не должны
терять нативный ИИ ради неподтверждённого расширения переписывания.
