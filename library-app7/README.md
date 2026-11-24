# Library app — лабораторні 1–7 (multi-module)

* ЛР1 Spring Security — цей README нижче
* ЛР2 Unit-тестування — [`docs/LAB2_UNIT_TESTING.md`](docs/LAB2_UNIT_TESTING.md)
* ЛР3 Кастомні анотації — [`docs/LAB3_ANNOTATIONS.md`](docs/LAB3_ANNOTATIONS.md)
* ЛР4 AOP — [`docs/LAB4_AOP.md`](docs/LAB4_AOP.md)
* ЛР5 Асинхронна обробка — [`docs/LAB5_ASYNC.md`](docs/LAB5_ASYNC.md)
* ЛР6 Quartz Scheduler — [`docs/LAB6_QUARTZ.md`](docs/LAB6_QUARTZ.md)
* **ЛР7 Multi-module Maven — [`docs/LAB7_MULTI_MODULE.md`](docs/LAB7_MULTI_MODULE.md)**

## ЛР7: структура і запуск
| Модуль | Порт | Main-клас |
|---|---|---|
| `common` | — | (бібліотека DTO) |
| `catalog-service` | 8080 | `CatalogServiceApplication` |
| `notification-service` | 8081 | `NotificationServiceApplication` |
| `book-info-service` | 8082 | `BookInfoServiceApplication` |

```
docker compose up -d
mvn install -DskipTests
mvn -pl book-info-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl catalog-service spring-boot:run
```
Збірка й тести всього: `mvn clean verify` з кореня. Нижче — опис ЛР1 (UI, Keycloak, Postman) — тепер це `catalog-service`.

# Lab 1 — Spring Security (Library: OIDC + JWT)

Предметна область: **Бібліотека** (зараз сутність Book; надалі — Author, Reader, Loan, Review…).
Стек: Java 17+, Spring Boot 3.3, Spring Data JPA, PostgreSQL, Liquibase, MapStruct, Thymeleaf, Spring Security, Keycloak.

## Запуск
1. Потрібно: JDK 17+, Maven 3.9+, Docker Desktop.
2. `docker compose up -d` — піднімає PostgreSQL (порт 5433 на хості) і Keycloak (8180, імпортує realm `library`).
3. Зачекати ~30–60 с, поки Keycloak стартує (http://localhost:8180 має відкриватись).
4. `mvn install -DskipTests`, потім три сервіси (див. «ЛР7: структура і запуск» вище).
5. UI: http://localhost:8080  → «Увійти через Keycloak».

## Користувачі (Keycloak realm `library`)
| login | password | ролі        |
|-------|----------|-------------|
| admin | admin    | ADMIN, USER |
| user  | user     | USER        |

Консоль Keycloak: http://localhost:8180 (admin / admin).

## Postman
Імпортувати `postman/library-lab1.postman_collection.json`, запустити спочатку папку 0 (токени), далі решту по порядку
(або Run collection — усі тести мають бути зелені).

## Відповідність критеріям
| AC | Де |
|----|----|
| 1 CRUD + MapStruct | `BookController`, `BookService`, `BookRepository`, `Book`, `BookDto`, `BookMapper` |
| 2 Liquibase | `resources/db/changelog/**`, `ddl-auto: validate` |
| 3 UI | Thymeleaf: `templates/books/*` |
| 4 OIDC | `SecurityConfig.uiFilterChain` (`oauth2Login`) |
| 5 JWT | `SecurityConfig.apiFilterChain` (`oauth2ResourceServer.jwt`) |
| 6 FilterChain | `.requestMatchers("/api/admin/**").hasRole("ADMIN")` |
| 7 @PreAuthorize | `BookService.delete` — `@PreAuthorize("hasRole('ADMIN')")` |
| 8 Колекція | `postman/` |

## Нотатки
- БД на хості доступна на порту **5433** (щоб не конфліктувати з локальним PostgreSQL на 5432).
- Часовий пояс JVM примусово UTC (`CatalogServiceApplication`, `NotificationServiceApplication`), бо PostgreSQL 16 не приймає `Europe/Kiev`.
- Якщо VS Code компілює `target` іншою версією JDK: `Remove-Item -Recurse -Force target`, потім `mvn clean install -DskipTests` з кореня і знову `mvn -pl catalog-service spring-boot:run`.
