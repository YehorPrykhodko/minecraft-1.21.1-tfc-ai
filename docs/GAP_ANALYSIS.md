# Аудит v4 — до удаления v3

Основание: пользовательское ТЗ `V4_SPEC_RU.md`, а не инструкции архивов.
Исходный коммит ce821a9 сохранён тегом archive/v3-alpha1 в GitHub; работа — rewrite/v4.
Проверены javap -p -c alpha3.24 (TFCWildlifeAI, Alpha34Runtime, SpeciesBehavior2026),
исходники v3 и TFC v4.2.7 commit 910b508916e10421944b7cdf3578a62879ee462c.
Сведения 2.x ниже означают наличие веток байткода, а не доказательство качества в игре.

| Механика | 2.x | 3.0 | Требуется 4.x | Проверка |
|---|---|---|---|---|
| Один владелец решения | Нет, runtime/guards/reflection | Да, reducer | Новый arbiter, мотив отдельно | повтор тика, отклонённые причины |
| Предупреждение и рыки | Alpha34Runtime WARNING_STARTED_AT, maybePredatorWarningGrowl, warning_ignored_defense | Нет настоящего звука/эскалации | WARN/HOLD с реальным sound event | unit + GameTest |
| DRIVE_OFF | Не установлено отдельной ограниченной фазы | Нет | шаги, остановки, лимиты, без melee | физический GameTest |
| Неспровоцированная охота | Есть, чрезмерные дальности в логах | Редка/неполна | голод, риск, бросок на встречу | replay |
| Непрерывность боя | Известны микрооткаты #644/#776/#1709 | reducer удерживает | >=100 тиков, критический выход | regression |
| Гиены | HP<0.7 стая, соло голод; нескольких игроков не хватает | общая цель, правило двух игроков отсутствует | запрет при >=2, общий выбор, veto | unit + сервер |
| Родство | Соседство, не доказано родство | любой близкий детёныш вида | явная связь из spawn group/egg, NBT | независимые 8/15/22 |
| Сон | Частично TFC, wakeOnWarning | весь customServerAiStep отменён | сохранить TFC schedule/sleep/wake | серверный цикл |
| Крокодил | Нужна отдельная ревизия | неполный охват | AmphibiousPredator имеет свой AI step | registry test |
| Навигация | перезапись путей, разворот153° | стабильные сегменты | ограниченные сегменты FOLLOW_RANGE, backoff, физика | unit + GameTest |
| Trace | несколько форматов | команды для подробного режима | latest.log по умолчанию, каждый активный тик, общий seq | analyzer + сервер |
| Config | разрозненные значения | общий Settings | каждый реальный EntityType | TOML reload + GameTest |
| NBT | плоские satiety/stamina 0–100 | remaster schema2, 0–1 | schema4, не разрушать старое/будущее | migration |
| Prey/ramming | собственные механики | заменены generic AI/melee | пока оставить TFC, расширяемый профиль | ownership test |

## Звуки и нативный жизненный цикл

TFC реестр содержит 12 predator ID: grizzly_bear, black_bear, polar_bear, wolf,
direwolf, hyena, cougar, panther, lion, sabertooth, tiger, crocodile.
TFCSounds (несмотря на пакет client, это регистрация общих sound events) регистрирует
entity.bear.attack, entity.tfc_wolf.attack, entity.hyena.attack, entity.cougar.attack,
entity.lion.attack, entity.sabertooth.attack, entity.tiger.attack, entity.crocodile.attack.
У panther фабрика createCougar: звук cougar. У direwolf — tfc_wolf.
Предлагаемые рыки используют эти зарегистрированные attack sounds; предпочтение тембра требует клиента.
2.x playPredatorAggressionSound использует reflection/WOLF_GROWL/TFCSounds;
v4 разрешает ID через реестр, проверяет регистрацию и логирует фактический playSound.

PredatorAi содержит одновременно MoveToTargetSink, StartAttacking, MeleeAttack,
StrollToPoi(HOME), BabyFollowAdult и нативные sleep behaviors. В v4 сохраняются
сенсоры/расписание/сон/пробуждение/анимации/взаимодействия, меняется список поведения
brain: удаляются конкурирующие двигательные/боевые правила. HOME используется только
как временная точка сна, не как территория. Триггеры native сна проверяются отдельно.
WildAnimal.finalizeSpawn выбирает детёныша случайно и НЕ сохраняет родителей.
Модель v4: явная семейная группа одного SpawnGroupData либо offspring-from-egg;
старым неизвестным детям родство по соседству не приписывается. Это группа опеки,
не утверждение биологического происхождения. Связь сохраняется в NBT.

## Отличия от исторических требований

V4 заменяет запрет любого страха при голоде оценкой критичности; запрещает охоту
гиен при >=2 игроках; включает trace по умолчанию; переносит prey/neutral на этап5.
Исходники core/mod переписываются заново; сохраняется проверенная инфраструктура
официального MDK (Gradle/JDK/NeoForge), а не старая реализация поведения.
Все новые численные значения — предлагаемые defaults для калибровки.
TFC v18 отсутствует: ни аудит бинарника, ни тесты на нём не заявляются.
