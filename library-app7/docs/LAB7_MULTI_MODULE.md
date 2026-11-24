# Лабораторна №7 — Multi-module Maven (мікросервіси, вступ)

Один репозиторій і один процес збірки (`mvn verify` з кореня) охоплюють **три незалежні сервіси** і спільний модуль DTO.

```
library-app7/
├── pom.xml                  батьківський POM: packaging=pom, <modules>, спільне
├── common/                  library-common — ЛИШЕ DTO-контракти між сервісами (jar, без Spring і main)
├── catalog-service/         :8080  книги, UI, OIDC+JWT, AOP (ЛР4), async-звіт (ЛР5), Quartz-звіт по каталогу (ЛР6)
├── notification-service/    :8081  листи читачам + Quartz-дайджест новинок (ЛР6), власна БД notification
├── book-info-service/       :8082  «зовнішній» сервіс рейтингу й наявності (з ЛР5), без БД
├── docker-compose.yml       postgres (БД library + notification) + keycloak; профіль apps — усі сервіси
└── docker/postgres/         скрипт створення БД notification
```

## Як сервіси взаємодіють

| Хто → кого | Як | Що передається (DTO з common) |
|---|---|---|
| catalog → notification | `POST /internal/notifications/book-created` + заголовок `X-Internal-Api-Key`; @Async після коміту (ЛР5) | `BookCreatedNotification` |
| catalog → book-info | `GET /api/book-info/rating?isbn=…`, `GET /api/book-info/availability?title=…&author=…`; два паралельні @Async-виклики + `thenCombine` (ЛР5) | `RatingInfo`, `AvailabilityInfo` |

Сервіси знають один про одного **лише URL** (`library.services.*` в `application.yml`) і **DTO з `common`**. Maven-залежності між сервісами немає.
Кожен сервіс має власні дані: notification-service не читає таблицю `books`, а веде свою чергу `pending_announcements` (ідемпотентно за `book_id`).

## Сценарії методички

| Сценарій | Де виконано |
|---|---|
| 1. Кореневий `pom.xml` з `packaging=pom`, у `<modules>` усі модулі; `<parent>` кожного модуля вказує на кореневий; `mvn verify` з кореня зелений | `pom.xml` (`<packaging>pom</packaging>`, `<modules>`); `common/pom.xml`, `*/pom.xml` → `<parent>library-parent</parent>`. Reactor: library-parent, library-common, catalog-service, notification-service, book-info-service — усі SUCCESS |
| 2. Спільна для всіх залежність — у `<dependencies>` кореневого POM, а не в модулях | `spring-boot-starter-test` (test) і `lombok` (optional) — лише в кореневому `<dependencies>`; у модулях їх немає. Версії `library-common`, `mapstruct` — у `<dependencyManagement>` кореня |
| 3. Власний main, `application.yml`, порт; плагін Spring Boot у POM модуля | `CatalogServiceApplication` :8080, `NotificationServiceApplication` :8081, `BookInfoServiceApplication` :8082; у кожного свій `src/main/resources/application.yml`; `spring-boot-maven-plugin` — у `<build>` кожного сервісу (у корені лише його налаштування в `<pluginManagement>`) |
| 4. `mvn dependency:tree`: жоден сервіс не залежить від іншого; спільний лише `common` з DTO | У кожного сервісу з `com.example` лише `library-common`. Плюс автоматичний захист: `maven-enforcer-plugin` (`bannedDependencies`) у кореневому POM — якщо додати залежність одного сервісу від іншого, `mvn verify` падає (`BUILD FAILURE … banned`) |
| Тести і Dockerfile у кожного сервісу | `*/src/test`, `*/Dockerfile`; покриття ≥ 60% перевіряє JaCoCo для КОЖНОГО модуля (плагін у кореневому `<build><plugins>`) |

Перевірка сценарію 4 вручну:
```
mvn install -DskipTests
mvn dependency:tree -pl catalog-service -Dincludes=com.example
mvn dependency:tree -pl notification-service -Dincludes=com.example
mvn dependency:tree -pl book-info-service -Dincludes=com.example
```
Результат у кожного: `\- com.example:library-common:jar:0.0.1-SNAPSHOT:compile`.

## Тести (`mvn clean verify` з кореня; Docker не потрібен)

| Модуль | Тестів | Покриття рядків | Що нового в ЛР7 |
|---|---|---|---|
| common | 4 | 100% | контракти DTO |
| catalog-service | 180 | ~86% | `ServiceClientsTest`: HTTP-контракт з notification/book-info через `MockRestServiceServer` (URL, ключ, тіло, розбір 422) |
| notification-service | 21 | ~90% | `NotificationServiceIntegrationTest`: весь сервіс на випадковому порту + H2 — 401 без ключа, ідемпотентний прийом, дайджест двічі, збій SMTP; `BookCreatedServiceTest`; тести дайджесту з ЛР6 |
| book-info-service | 10 | ~90% | контролер (JSON, 422), детермінованість, старт сервісу (`@SpringBootTest` на випадковому порту) |

## Запуск

**Локально (як раніше, профіль `local` за замовчуванням):**
1. `docker compose up -d` — PostgreSQL (БД `library` і `notification`) + Keycloak.
2. `mvn install -DskipTests` — один раз (щоб сервіси бачили `library-common`).
3. Три термінали (або Run main-класів в IDE):
   ```
   mvn -pl book-info-service spring-boot:run
   mvn -pl notification-service spring-boot:run
   mvn -pl catalog-service spring-boot:run
   ```

**У контейнерах:**
```
mvn package -DskipTests
docker compose --profile apps up -d --build
```
Keycloak у контейнері видає токени з `iss = http://localhost:8180/...`, а всередині контейнера `localhost` — сам контейнер.
Тому catalog-service у профілі `docker` (`application-docker.yml`) ходить у Keycloak через `host.docker.internal:8180`,
а поле `iss` JWT перевіряє як `localhost:8180`. Конфігурацію Keycloak не змінено.

## Перевірено вживу (PostgreSQL 16 + три контейнери)
* створення книги в catalog → notification-service надіслав лист і записав книгу у **свою** БД (`pending_announcements`);
* звіт: catalog → book-info-service, два паралельні виклики, ~1,2 с, `complete=true`; книга без ISBN → 422 від book-info → пояснення у звіті, HTTP 200;
* внутрішнє API notification без ключа → 401;
* **незалежність:** book-info і notification зупинено → звіт у catalog усе одно 200 (дані «недоступні» з поясненням), книга створюється (201), збій відправки фіксує `AsyncUncaughtExceptionHandler`.

Postman: папка **«10. Lab 7 - Multi-module (3 сервіси)»**.
