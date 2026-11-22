# Лабораторна №6 — Quartz Scheduler

## Рутинні задачі бібліотеки
| Завдання (Job) | Що робить | Тригери | Стратегія збою |
|---|---|---|---|
| `NewBooksDigestJob` (`notifications.newBooksDigest`) | Один лист-дайджест підписникам про книги, які ще не анонсувались (`books.announced_at IS NULL`), і позначає їх анонсованими | `notifications.newBooksDigestHourly`: SimpleTrigger, кожні 60 хв | **Повтор з обмеженням**: `refireImmediately`, поки `refireCount < maxRetries` (2), далі ERROR у лозі й очікування наступного запуску |
| `CatalogReportJob` (`reports.catalogReport`) | Знімок статистики каталогу (кількість книг, з ISBN, топ авторів) у таблицю `catalog_reports` | **два**: `reports.catalogReportDaily` (cron `0 0 6 * * ?`) і `reports.catalogReportWeekly` (cron `0 0 7 ? * MON`) | **Фіксація помилки й очікування наступного запуску** (без повторів) |

## Реалізація за пунктами методички
1. **Схема для своєї БД**: `tables_postgres.sql` з `quartz-2.3.2.jar` (`org/quartz/impl/jdbcjobstore/`).
2. **Liquibase**: `db/changelog/changes/003-quartz-tables.yaml` переносить у YAML 1:1 усі 11 таблиць, 20 індексів і 5 FK.
   Плюс `004-books-announced-at.yaml` і `005-catalog-reports.yaml` для доменних даних.
3. **JDBC job store** (`application.yml`, `spring.quartz.*`): `job-store-type: jdbc`, `initialize-schema: never`,
   `PostgreSQLDelegate`, `useProperties: true`, `isClustered: true`, `overwrite-existing-jobs: true`.
4. **Два завдання, усі JobDetail і Trigger в одному `@Configuration`**: `scheduling/QuartzJobsConfig`. У `CatalogReportJob` два тригери.
5. **JobDataMap**: значення за замовчуванням живуть у JobDetail, а тригер перевизначає лише частину ключів:

   | | `period` | `topAuthors` |
   |---|---|---|
   | JobDetail (за замовчуванням) | `DAILY` | `5` |
   | щоденний тригер | — | — |
   | щотижневий тригер | **`WEEKLY`** | **`10`** |
   | дайджест (JobDetail) | `batchSize=50`, `recipients=readers@library.local`, `maxRetries=2` | |
6. **Ідемпотентність** (бізнес-логіка в сервісах, межа транзакції — `@Transactional` метод сервісу):
   * `NewBooksDigestService.sendDigest`: лист і позначка `announced_at` в одній транзакції. Повторний запуск не знаходить нових книг.
     Якщо лист не пішов, транзакція відкочується, і наступна спроба надішле ті самі книги.
   * `CatalogReportService.generate`: рядок звіту визначається парою `(period, period_start)` з UNIQUE-обмеженням.
     Повторний запуск за той самий період не створює дубль, а якщо дані не змінились, не змінює навіть `generated_at`.
7. **Стратегії збою**: `scheduling/JobFailures` — `retryWithLimit` для дайджесту і `logAndWaitForNextRun` для звіту.

### Інші рішення
* **«Легкі» Job-и**: кожен лише читає параметри (`JobParams`) і робить один виклик сервісу. Spring інжектить сервіс через конструктор (`SpringBeanJobFactory`).
* **Явні помилки параметрів** (`JobParams`):
  * відсутній ключ → `JobExecutionException("required JobDataMap key 'recipients' is missing")`;
  * одруківка в ключі тригера (`topAuthor`) → `unknown JobDataMap key(s) [topAuthor] — expected only [period, topAuthors]`;
  * не число → `'batchSize' must be an integer, but was 'fifty'`.

  Значень за замовчуванням у коді Job-а немає: вони задані лише в JobDetail.
* **`@DisallowConcurrentExecution`** на обох завданнях. Два одночасні дайджести взяли б ті самі книги, тому другий запуск
  чекає (тригер у стані `BLOCKED`). З `isClustered: true` це працює і між кількома інстансами.
* **Misfire** (застосунок був вимкнений у момент запуску):
  * дайджест: `RESCHEDULE_NEXT_WITH_REMAINING_COUNT` — пропущені запуски не «доганяються» пачкою;
  * обидва звіти: `FIRE_ONCE_NOW` — пропущений звіт формується один раз одразу після старту.
* **Часовий пояс cron**: `library.scheduling.time-zone: Europe/Kyiv`, тож розклад не залежить від пояса сервера (06:00 Київ = 04:00 UTC взимку).
  Пастка: `TimeZone.getTimeZone("Europe/Kyev")` з одруківкою не кидає виняток, а повертає **GMT**. `QuartzTimeZones` пише про це WARN, а тест фіксує таку поведінку.
* **Перезапуск і деплой**: розклад і стан тригерів зберігаються в `QRTZ_*`. `overwrite-existing-jobs: true` означає, що при деплої
  JobDetail/Trigger з коду оновлюють збережені за тими самими ключами. Дублів немає, і зміни розкладу в коді застосовуються.
  Збій посеред виконання: `requestRecovery()` для дайджесту, тож після рестарту він виконається знову (безпечно завдяки ідемпотентності).

## Знайдено під час перевірки на справжньому PostgreSQL
Узагальнений тип Liquibase `BLOB` на PostgreSQL перетворюється на **`oid`**, а Quartz пише туди `bytea`. Через це застосунок не стартував:
`column "job_data" is of type oid but expression is of type bytea`. На H2 тести проходили.
Рішення: у `003-quartz-tables.yaml` тип задано явно через `${quartz.binary.type}` (`BYTEA` для postgresql, `BLOB` для h2).

## Тести (`mvn clean verify`, Docker не потрібен)
| Сценарій методички | Тест |
|---|---|
| Jobs і тригери в одному місці; JobKey; name+group | `QuartzJobsConfigTest.jobs_haveExplicitNameAndGroup`, `triggers_haveExplicitNameAndGroup_andPointToTheirJobByJobKey` |
| Параметри тригера мають пріоритет | `QuartzJobsConfigTest.mergedJobDataMap_*`: об'єднання робить справжній код Quartz (`JobExecutionContextImpl.getMergedJobDataMap`) |
| Job «легкий»: параметри + один виклик сервісу, транзакція в сервісі | `JobsTest.digestJob_readsParamsAndCallsServiceOnce`, `reportJob_weeklyOverrides_passedToServiceOnce`, `jobs_haveSingleDependencyAndNoTransactionalAnnotations`, `services_ownTheTransactionBoundary` |
| Невідповідність параметрів → явна помилка | `JobsTest.digestJob_missingKey_*`, `reportJob_typoInTriggerKey_*`, `digestJob_notANumber_*`, `reportJob_unknownPeriod_*` |
| Паралельне виконання заборонено | `QuartzRuntimeBehaviourTest.digestJob_secondFireWhileFirstRunning_waitsUntilFirstFinishes` (справжній планувальник: другий тригер `BLOCKED`, ніколи не 2 одночасно) + `JobsTest.jobs_areAnnotatedWithDisallowConcurrentExecution` |
| Misfire-інструкція кожного тригера | `QuartzJobsConfigTest.triggers_haveExplicitMisfireInstructions` |
| Cron-тригери: явний пояс; невідомий → GMT | `QuartzJobsConfigTest.cronTriggers_haveExplicitTimeZoneAndSchedule`, `cronTrigger_unknownTimeZoneId_silentlyFallsBackToGmt`, `QuartzTimeZonesTest` |
| Повторний запуск безпечний (**IT**) | `QuartzSchedulingIntegrationTest.digest_runTwiceForSameData_secondRunChangesNothing`, `report_runTwiceForSamePeriod_secondRunChangesNothing`, `report_catalogChangedBetweenRuns_*`, `digest_mailFails_nothingIsMarked_*` |
| Невдале виконання обробляється цілеспрямовано, без нескінченного циклу | `JobsTest.digestJob_failureBeforeLimit_*`, `digestJob_failureAfterLimit_*`, `reportJob_failure_*`; `QuartzRuntimeBehaviourTest.digestJob_alwaysFailing_isRetriedLimitedTimesThenStops` (справжній Quartz: рівно 3 виконання) |
| Розклад у БД через Liquibase, `initialize-schema: never` (**IT**) | `QuartzSchedulingIntegrationTest.liquibase_createsAllQuartzTables_*`, `scheduler_usesJdbcJobStore_andPersistsJobsAndTriggersFromConfig`; `QuartzEndToEndIntegrationTest` (запущений планувальник з JDBC store виконує Job через Spring) |

Integration-тести піднімають мінімальний Spring Boot застосунок (`QuartzItSupport`): справжні Liquibase, JPA і Quartz на H2 в пам'яті,
з тим самим `application.yml`. Відрізняються лише URL БД і `StdJDBCDelegate` (для H2).

## Ручна перевірка (Docker + Keycloak + `mvn spring-boot:run`)
* У лозі під час старту: `Using job-store 'org.springframework.scheduling.quartz.LocalDataSourceJobStore' - which supports persistence. and is clustered.`
* Postman, папка **«9. Lab 6 - Quartz»**: розклад (`GET /api/admin/scheduler/jobs`), ручний запуск, ідемпотентність звіту, 403 для USER, 404 для неіснуючого завдання.
* Щоб швидко побачити дайджест за розкладом: `mvn spring-boot:run -Dspring-boot.run.arguments=--library.scheduling.digest-interval-minutes=1`,
  створити книгу, і за хвилину в лозі з'явиться `[digest] sent digest with 1 book(s)`.
* Повтор з обмеженням: додатково `--library.simulation.mail-failure=true`. У лозі буде `attempt 1/3`, `attempt 2/3`, `giving up until the next scheduled run`.
* У БД: `select * from qrtz_triggers;` `select * from catalog_reports;`
