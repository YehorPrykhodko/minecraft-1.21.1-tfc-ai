# Сборка и установка v4

Зафиксированный стек: Java 21, Gradle Wrapper 8.14.3 (с checksum), ModDevGradle
2.0.148, MC 1.21.1, NeoForge 21.1.235, TFC 4.2.7, Patchouli 1.21.1-92-neoforge,
неофициальный порт TaCZ 1.1.8-hotfix-r3 для NeoForge 1.21.1.
Оболочка сборки сохранена после ревизии официального MDK
NeoForgeMDKs/MDK-1.21.1-ModDevGradle, commit e93d7f09d4eec12c76d1d7be9bf4eaad0246e9e0.
Все рабочие Java-классы v3 заменены новыми в `fr.ypwildlife`.

```bash
./gradlew -PcoreOnly :core:build
./gradlew build
./gradlew :mod:runGameTestServer
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Зависимости TFC/Patchouli/TaCZ разрешаются через официальный Maven API Modrinth.
Не копировать одновременно эти зависимости в runtime и не добавлять старый Wildlife.
`validateRuntimeMods` требует по одному экземпляру каждой зависимости.
JAR: `mod/build/libs/tfc-wildlife-ai-remaster-4.0.0-alpha.1.jar`.
Версия с `-sources` не предназначена для установки в игру.

В облаке сначала `source scripts/cloud-env.sh`, либо `bash scripts/setup-cloud.sh`.
Скрипт устанавливает проверенный JDK 21, импортирует сертификат прокси в отдельное
хранилище и запускает проверки. Проверка TLS не отключается.

Исторический HTTP 403 означал отсутствие maven.neoforged.net в **применённом**
сетевом списке. После публикации разрешения тот же URL
`https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom`
вернул 200, равно как POM NeoForge 21.1.235. Версии менять ради обхода 403 не пришлось.
Сохранение черновика настроек облака само по себе не применяет разрешения.

## Установка

В отдельной копии игрового профиля установить Minecraft/NeoForge указанной версии,
TFC 4.2.7, его Patchouli, TaCZ hotfix-r3 и один новый Wildlife JAR.
Старые Wildlife v2/v3 не должны загружаться одновременно с v4.
Обычный клиент/сервер запускается пользователем; GameTestServer работает без GUI.

При первом запуске NeoForge создаёт `tfc_wildlife_ai-v4-server.toml` в серверной
папке конфигурации мира; GameTest использует `run/gametest/config`.
Файл `tfc_wildlife_ai-server.toml` от v3 **не перезаписывается новой спецификацией**.
Переносить индивидуальные изменения можно перед первым запуском v4:

```bash
python3 scripts/migrate_config_v3.py /path/tfc_wildlife_ai-server.toml \
  --output /path/tfc_wildlife_ai-v4-server.toml
```

Скрипт требует Python 3.11+, сохраняет исходный файл, отказывается перезаписывать
существующий результат и сообщает о несовместимых значениях. По умолчанию переносит
значения, отличные от defaults v3, оставляя предложенные defaults v4 для остальных.
`--include-defaults` переносит и стандартные числа v3. `panicExit` не имеет прямого
соответствия: новая модель использует оценку риска и время восстановления.
Старый переключатель debug не переносится: trace alpha включена согласно ТЗ v4.
NeoForge дополняет отсутствующие поля при загрузке. Это явный инструмент миграции,
а не молчаливое изменение старых настроек.

## TFC v18

Для отдельной компиляции с доступным пользовательским файлом:

```bash
./gradlew build :mod:runGameTestServer -PtfcJar=/absolute/path/to/TFC-v18.jar
```

Эта команда пока **не выполнялась**: v18 недоступен. Нельзя устанавливать одновременно
стандартный и модифицированный TFC. Успешная компиляция с v18 также не заменит
проверку походки, храпа, отсутствия Z и взаимодействий в настоящем клиенте.
