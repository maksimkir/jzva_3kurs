# Лабораторна №5 — Асинхронна обробка

## Три повільні побічні ефекти (~1 с кожен)

| # | Побічний ефект | Де | Патерн |
|---|---|---|---|
| 1 | Лист підписникам про нову книгу (SMTP) | `notification/BookNotificationService.notifyNewBook` — `@Async`, `void` | **fire-and-forget**: викликається з `BookService.create` після коміту транзакції |
| 2 | Рейтинг книги із зовнішнього сервісу рецензій | `external/ExternalBookInfoClient.fetchRating` — `@Async`, `CompletableFuture<RatingInfo>` | **результат потрібен викликаючому коду** |
| 3 | Наявність у бібліотеках-партнерах (зовнішній каталог) | `external/ExternalBookInfoClient.fetchAvailability` — `@Async`, `CompletableFuture<AvailabilityInfo>` | — // — |

2 і 3 незалежні, тому `report/BookReportService` запускає їх одночасно й об'єднує через **`thenCombine`**
у `BookReportDto` → `GET /api/books/{id}/report` (~1 с замість ~2 с).

Повільність імітує `async/LatencySimulator` (`library.simulation.latency-ms: 1000`), реальні SMTP/API не потрібні.
Відповіді «зовнішніх сервісів» детерміновані (залежать від ISBN/назви).

## Власний виконавець (`async/AsyncConfig`)
```yaml
library:
  async:
    core-size: 4
    max-size: 8
    queue-capacity: 50
    thread-name-prefix: library-async-
    call-timeout-ms: 2000
    report-timeout-ms: 3000
```
* Бін `ThreadPoolTaskExecutor` з ім'ям `libraryTaskExecutor`; усі `@Async("libraryTaskExecutor")` посилаються на нього явно,
  `AsyncConfigurer.getAsyncExecutor()` теж повертає його. Spring Boot через наш бін `Executor` не створює свій `applicationTaskExecutor`,
  і `SimpleAsyncTaskExecutor` не використовується.
* Обмежені ресурси: максимум 8 потоків + 50 у черзі. Далі `AbortPolicy` → `TaskRejectedException`.
* `waitForTasksToCompleteOnShutdown=true`, щоб не губити листи під час зупинки.

## Обробка збоїв
| Ситуація | Рішення |
|---|---|
| `@Async` з `CompletableFuture` кидає виняток (рейтинг для книги без ISBN) | `exceptionally(...)` → `RatingInfo.unavailable("…")`, звіт `complete=false`, **HTTP 200** |
| `@Async void` кидає виняток (SMTP недоступний) | `LoggingAsyncUncaughtExceptionHandler`: ERROR у лозі зі стеком + лічильник збоїв. Помилка не зникає непомітно |
| Зовнішній сервіс «завис» | `orTimeout(call-timeout-ms)` на кожному виклику → fallback «тайм-аут»; очікування всього звіту теж обмежене: `get(report-timeout-ms)` → частковий звіт |
| Пул переповнений, сповіщення | `BookService` ловить `TaskRejectedException` → WARN, книгу створено (сповіщення працює за принципом best effort) |
| Пул переповнений, звіт | `GlobalExceptionHandler` → **503 Service Unavailable**, «Спробуйте, будь ласка, пізніше», `Retry-After: 5`. Не випадкова 500 |

Лист ставиться в чергу **після коміту** (`TransactionSynchronization.afterCommit`), щоб не надсилати листів про книгу, яка відкотилась.
`@Async`-методи винесено в окремі біни: виклик іде через проксі, інакше спрацювала б та сама проблема self-invocation, що в ЛР4.

## Тести (`mvn clean verify`, Docker/Keycloak/БД не потрібні)
Мінімальний Spring-контекст `@SpringJUnitConfig` (НЕ `@SpringBootTest`) з **реальним** `AsyncConfig`.
Повільність керується з тесту через latch/barrier, без `Thread.sleep`.

| Сценарій методички | Тест |
|---|---|
| Повільний метод не блокує викликаючий код; завершується пізніше в потоці нашого executor | `AsyncProcessingTest.create_returnsBeforeEmailIsSent_andEmailIsSentLaterInOwnExecutorThread`: SMTP «притримано», а `create()` уже повернувся; після відпускання лист іде в `library-async-*` |
| Виконавець явний і обмежений; потік з префіксом, не дефолтний пул і не `SimpleAsyncTaskExecutor` | `executor_isExplicitThreadPoolWithBoundedResources` + перевірки імені потоку в інших тестах |
| Два виклики справді одночасні й об'єднуються | `report_twoExternalCallsRunConcurrently_andResultsAreCombined`: `CyclicBarrier(2)` пропускає, лише якщо обидва виклики працюють одночасно; потоки різні |
| Збій async-виклику не валить викликаючий код (`exceptionally`) | `report_ratingCallFails_requestStillSucceedsWithFallback`, `BookReportServiceTest.buildReport_availabilityFails_*` |
| `AsyncUncaughtExceptionHandler` фіксує помилку `@Async void` | `notifyNewBook_asyncVoidFails_errorIsRecordedByUncaughtExceptionHandler` |
| Очікування обмежене тайм-аутом | `report_externalServiceHangs_callerStopsWaitingAfterTimeout` (`orTimeout`), `BookReportServiceTest.buildReport_overallWaitExceeded_*` (`get(timeout)`) |
| Переповнення виконавця обробляється свідомо | `ExecutorRejectionTest` (пул 1 потік + черга 1): 3-тє завдання → `TaskRejectedException`; `create` не падає; звіт → виняток → `BookReportEndpointTest.report_executorOverloaded_returns503TryLater` (503, а не 500) |

## Ручна перевірка
Docker + Keycloak + `mvn spring-boot:run`, Postman, папка **«8. Lab 5 - Async»**:
1. `POST /api/books` відповідає швидше ніж за секунду, а в консолі десь через 1 с з'являється `[mail] sent ... (thread library-async-1)`.
2. `GET /api/books/{id}/report`: `elapsedMs` ≈ 1000 (а не 2000), у консолі два рядки `[external]` з різних потоків `library-async-*`.
3. Книга без ISBN → звіт 200, `rating.available=false` з поясненням.
4. Щоб побачити `AsyncUncaughtExceptionHandler`, увімкніть `library.simulation.mail-failure: true` і створіть книгу:
   у консолі з'явиться `ERROR [async] fire-and-forget task BookNotificationService.notifyNewBook failed ...`.
