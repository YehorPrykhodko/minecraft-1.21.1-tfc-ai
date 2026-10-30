# Сборка и запуск

Целевой стек: JDK 21, Minecraft **1.21.1**, NeoForge **21.1.235**,
TerraFirmaCraft **4.2.7**, Patchouli **1.21.1-92-NEOFORGE**,
TaCZ NeoForge port **1.1.8-hotfix-r3**. Версии зафиксированы в `gradle.properties`.

```bash
./gradlew build
./gradlew :mod:runGameTestServer
```

Сборка автоматически получает стандартные TFC/Patchouli/TaCZ через официальный
Maven API Modrinth. Ручное заполнение `local-deps/` больше не требуется.
Только для независимых проверок ядра доступен `./gradlew -PcoreOnly build`.

Готовый мод: `mod/build/libs/tfc-wildlife-ai-remaster-3.0.0-alpha.1.jar`.
В него входят новые классы core и адаптера, а также серверные smoke-тесты.
`core/build/libs/core-*.jar` **не является игровым модом**.
Legacy JAR, классы TFC/TaCZ и их ресурсы не включаются в результат.

## Основа Gradle и причина прежнего 403

Проверен официальный `NeoForgeMDKs/MDK-1.21.1-ModDevGradle`, commit
`e93d7f09d4eec12c76d1d7be9bf4eaad0246e9e0`: ModDevGradle **2.0.148**,
Java toolchain 21, стандартные client/server/gameTestServer run configurations.
Сохранён Gradle Wrapper **8.14.3** с официальным SHA-256. NeoForge оставлен
**21.1.235**, согласно заданию; текущий MDK по умолчанию использует более новую
21.1.x. Дополнительный Parchment и автоматический Foojay не нужны для этой
сборки: имена Mojang и установленный JDK 21 достаточны.

URL `maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom`
и версия корректны. В предыдущей сессии домен отсутствовал в применённой
сетевой политике, поэтому запрос блокировался 403. После публикации настроек
тот же URL отвечает 200 и возвращает POM `net.neoforged:neoform-runtime:2.0.31`.
Также проверен POM `net.neoforged:neoforge:21.1.235`.
Подмена версии, отключение TLS и обход разрешений не потребовались.

Нужны Maven NeoForge, Maven Central, Gradle Plugin Portal, GitHub/Gradle
Distributions, `api.modrinth.com`, `cdn.modrinth.com`, `libraries.minecraft.net`,
`piston-meta.mojang.com`, `piston-data.mojang.com`,
`resources.download.minecraft.net`. Последний нужен для assets при userdev-запуске.

## Облачная машина

```bash
bash scripts/setup-cloud.sh
source scripts/cloud-env.sh
./gradlew build
./gradlew :mod:runGameTestServer
```

Setup сохраняет проверенный по SHA-256 JDK 21 в `/workspace/tools` и импортирует
предоставленный платформой CA в отдельное хранилище. `cloud-env.sh` активирует
его и Java HTTPS proxy, не меняет системный JDK и не сохраняет секреты.
TLS/проверка артефактов остаются включёнными. Для другой папки задайте
`WILDLIFE_TOOL_ROOT`. Setup и GameTestServer завершаются; фоновый сервер не остаётся.

## Запуск разработки

```bash
./gradlew :mod:validateRuntimeMods
./gradlew :mod:runClient
./gradlew :mod:runServer
```

`validateRuntimeMods` проверяет фактически разрешённые runtime-зависимости,
а не содержимое произвольной локальной папки. TFC, TaCZ и Patchouli должны
быть представлены ровно по одному. Старый Wildlife рядом с новым запрещён:
mod id `tfc_wildlife_ai` сохранён.

Обычный dedicated server использует `run/server`; при первом запуске ознакомьтесь
с Minecraft EULA и укажите своё согласие в `eula.txt`. GameTestServer —
официальный автоматический серверный тестовый режим; EULA-файл пользователя
для него не изменяется. Все четыре smoke-теста запускаются в `run/gametest`.

TFC подключён как полноценная compile/runtime-зависимость. Три Mixin используют
ссылки на `Predator.class`, `Prey.class`, `RammingPrey.class`; `@Pseudo` удалён.
В запуске проверяются также наследующие Predator гиены и доступ к пробуждению.

TaCZ hotfix-r3 проверен по опубликованному SHA-512. У него есть GunFireEvent,
но нет GunFirePostEvent: адаптер явно сообщает о fallback на событие перед
выстрелом. Отменённые события игнорируются. Синтетический event smoke-теста
не доказывает корректность пустого магазина/реального оружейного пакета.

## Пользовательский TFC v18

Для последующей проверки можно использовать:

```bash
./gradlew -PtfcJar=/absolute/path/TerraFirmaCraft-v18.jar build
./gradlew -PtfcJar=/absolute/path/TerraFirmaCraft-v18.jar :mod:runGameTestServer
```

Это заменяет Maven-зависимость TFC одним внешним файлом; Patchouli и TaCZ
остаются зафиксированными. Чужие JAR не коммитятся. Сейчас v18 недоступен:
стандартная 4.2.7 **не подтверждает** совместимость его анимаций и изменений.

CI выполняет core build, полный build и GameTestServer; загрузка CI-артефактов
происходит только после успешного завершения. Локальный проверенный игровой
JAR также сохраняется в `releases/` вместе с SHA-256.
