# Фактическая проверка — 30 октября 2026

**Полная сборка проходит. Игровой JAR собран. Headless NeoForge GameTestServer
со стандартным TFC 4.2.7 запущен и завершил все четыре обязательных smoke-теста.**
Пользовательский TFC v18 и графический клиент не проверены.

## Стек и повторяемые команды

Temurin JDK **21.0.8+9**, Gradle Wrapper **8.14.3**, ModDevGradle **2.0.148**,
Minecraft **1.21.1**, NeoForge **21.1.235**, TFC **4.2.7**, Patchouli **92**,
TaCZ NeoForge port **1.1.8-hotfix-r3**, JUnit Jupiter **5.11.4**.
JDK/Gradle проверены по официальным SHA-256; три игровых зависимости —
по SHA-512 из Modrinth API перед подключением через Maven.

| Проверка | Фактический результат |
|---|---|
| `./gradlew build` | PASS, Minecraft-адаптер и весь проект скомпилированы, mod JAR создан |
| `./gradlew -PcoreOnly :core:test --rerun-tasks` | PASS: **72 теста**, 0 failures/errors/skipped |
| `./gradlew :mod:validateRuntimeMods` | PASS: разрешены ровно по одному TFC, TaCZ, Patchouli |
| `./gradlew build :mod:runGameTestServer` | PASS: реальные server world/entity ticks, **4/4 обязательных GameTest** |
| `bash scripts/setup-cloud.sh` после обновления скрипта | PASS: core build, full build и повторный GameTestServer, exit 0 |
| Все три Mixin в серверном логе | Применены к Predator, Prey, RammingPrey |
| Содержимое mod JAR | Новый core/адаптер/Mixin/metadata присутствуют; legacy и чужих классов/JAR нет |
| GitHub Actions | Workflow настроен на build и GameTestServer; результат удалённого запуска не подменяется локальным |
| TFC v18 / графический клиент / полный ручной план | НЕ ПРОВЕРЕНЫ |

GameTestServer — настоящий серверный запуск Minecraft с модами и ресурсами,
а не mock Minecraft API. Обычный dedicated server с пользовательским миром
и интерактивный клиент в этой сессии не запускались. EULA-файл не изменялся.

## Серверные smoke-тесты

| GameTest | Проверяемый результат |
|---|---|
| allTfcBrainMixinsOwnLiveEntities | Живые grizzly, hyena, deer, boar получили BrainOwner; один runtime управляет ими; wake accessor вызывает TFC |
| persistentDataMigratesWithoutDestroyingLegacy | Реальный persistent CompoundTag: миграция шкалы ресурсов, старые данные сохранены, будущая schema не перезаписывается |
| damagedGrizzlyCommitsAndDealsRealMeleeDamage | Реальный урон вызывает самооборону; цель удерживается; атакующий получает фактический melee damage |
| taczServerEventInterruptsCombatWithRealPanic | Настоящий TaCZ GunFireEvent на NeoForge bus прерывает бой; создаётся путь; через 25 ticks животное физически дальше от угрозы |

Последний тест публикует синтетическое событие TaCZ на серверной шине.
Это проверка API-интеграции и реакции, **не ручной выстрел из оружия**,
не проверка пустого магазина/отмены конкретным оружейным скриптом.

## Независимые тесты ядра

| Набор | Число |
|---|---:|
| AcceptanceCasesTest — все поставленные сценарии | 16 |
| DecisionReducerTest — combat/fear/injury/hysteresis/reason/cooldown/random | 15 |
| NavigationExecutorTest — стабильность, repath, backoff и veto | 9 |
| PackCoordinatorTest — HP игрока, синхронизация, границы/состав стаи | 11 |
| SensesAndPersistenceTest — LOS/FOV/слух, ресурсы, личность, скорости | 10 |
| SpeciesAndFamilyTest — детёныши, защита, кошачьи, смерть цели | 7 |
| TraceAndReplayTest — JSON/veto/privacy и #644/#776/#1709 | 4 |

Replay читает реальные строки routine causes старых журналов и подаёт
нормализованные наблюдения в ядро с явными HP=100%, fear=0 и типичной дистанцией.
Это регрессия логического инварианта, а не симуляция старого мира.

## Причина 403 и исправления подключения

В старой применённой сетевой политике не было maven.neoforged.net.
Сохранённый черновик не менял работающий прокси. После публикации настройки
активная политика содержит домен, а **тот же URL** отвечает HTTP 200:

```
https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom
```

POM подтверждает group/artifact/version. POM NeoForge 21.1.235 также получен.
Поэтому не требовалось подменять версию NeoFormRuntime или выключать TLS.
Конфигурация сверена с официальным MDK 1.21.1, точный commit указан в BUILD_RU.

Gradle теперь сам разрешает стандартный TFC/Patchouli/TaCZ через Maven Modrinth.
TFC находится на compile classpath; Mixin используют его реальные классы,
без @Pseudo. validateRuntimeMods проверяет resolved runtimeClasspath.

Серверная проверка выявила ещё ограничение реального PathNavigation:
фиксированная точка FLEE в 24 блоках может лежать вне FOLLOW_RANGE (обычно 16).
Длина шага теперь ограничивается этим атрибутом; на открытом тестовом полу
проверено физическое бегство. Также источник звука выстрела использует eye position.
Первоначальная тестовая арена спавнила мобов внутри пола: неверная высота и
размер исправлены, проверки паники/движения сохранены и усилены.

## Артефакты и границы проверки

Готовая копия: `releases/tfc-wildlife-ai-remaster-3.0.0-alpha.1.jar`, SHA-256 рядом.
Новый JAR содержит только код этого проекта и собственную тестовую структуру;
TFC, TaCZ и Patchouli устанавливаются отдельно. Исходный код, CI, отчёты и
сборочный JAR сохраняются в GitHub, ветка main.

Выводы команд находятся в docs/validation: unit-tests.txt, full-build.txt,
gametest-server.txt, setup-and-full-build.txt, runtime-prerequisites.txt.
JUnit XML скопированы из текущего запуска; первичный 403 сохранён отдельно.

Наблюдались предупреждения сторонних модов о dev refmap, Java compatibility
у TaCZ и пойманные TFC обращения к client recipe classes при первичной
server resource reload. Они не остановили загрузку и GameTest. В hotfix-r3
отсутствует GunFirePostEvent: используется явно объявленный fallback GunFireEvent.
Ни один third-party JAR ради прохождения тестов не патчился.

Это ранняя alpha: перенос специального ram-разгона/телеграфирования и нативного
расписания сна не завершён (см. IMPLEMENTATION_RU). Настоящие игровые ситуации,
анимации, нагрузка и пользовательский TFC v18 проверяются отдельно по
IN_GAME_TEST_PLAN_RU. Успешная стандартная 4.2.7 не означает совместимость v18.
