# Lab 1 — Spring Security (Library: OIDC + JWT)

Предметна область: **Бібліотека** (зараз сутність Book; надалі — Author, Reader, Loan, Review…).
Стек: Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL, Liquibase, MapStruct, Thymeleaf, Spring Security, Keycloak.

## Запуск
1. Потрібно: JDK 21, Maven 3.9+, Docker Desktop.
2. `docker compose up -d` — піднімає PostgreSQL (5432) і Keycloak (8180, імпортує realm `library`).
3. Зачекати ~30–60 с, поки Keycloak стартує (http://localhost:8180 має відкриватись).
4. `mvn spring-boot:run` (або запуск `LibraryApplication` з IDE).
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
