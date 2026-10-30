# TFC Wildlife AI Remaster — source rewrite

Новый Java 21 / Gradle проект для Minecraft 1.21.1 и NeoForge 21.1.235.
Mod id: `tfc_wildlife_ai`. Целевые зависимости: TFC 4.2.7, TaCZ 1.1.8.
Старые бинарные файлы находятся только в `legacy/` для функционального аудита.

`core/` содержит неизменяемые снимки восприятия, предложения видов, единый
арбитраж решений, координацию стай и единственный исполнитель маршрутов.
`mod/` содержит серверные события NeoForge, адаптер навигации, три TFC mixin,
диагностику, конфигурацию, persistence и обработку выстрелов TaCZ.

```bash
./gradlew -PcoreOnly build  # независимые автоматические проверки
./gradlew build            # полная сборка мода с userdev
./gradlew :mod:runGameTestServer  # реальные серверные smoke-тесты с TFC
```

Состояние фактической проверки: [VALIDATION_RU.md](docs/VALIDATION_RU.md).
Инструкции сборки: [BUILD_RU.md](docs/BUILD_RU.md).
Аудит: [AUDIT.md](docs/AUDIT.md).
Реализованная архитектура: [IMPLEMENTATION_RU.md](docs/IMPLEMENTATION_RU.md).
Проверка в игре: [IN_GAME_TEST_PLAN_RU.md](docs/IN_GAME_TEST_PLAN_RU.md).

Начальные требования сохранены в `TASK_FOR_CODEX_RU.md`, `docs/BEHAVIOR_SPEC_RU.md`
и `tests/acceptance_cases.json`. `README_RU.md` описывает исходный стартовый пакет,
а не статус готовности новой реализации.

Стандартные TFC 4.2.7, Patchouli 92 и TaCZ 1.1.8-hotfix-r3 загружаются Gradle
автоматически. Проверенная сборка и SHA-256 сохраняются в `releases/`.
Пользовательский TFC v18 пока не проверен.
