# Лабораторна №4 — AOP (Aspect Oriented Programming)

Залежність: `spring-boot-starter-aop`. Усе в пакеті `com.example.library.aop`.

## Три пари «анотація + аспект»

| # | Анотація | Аспект / Advice | Наскрізна задача | Помітний ефект | Де застосовано |
|---|---|---|---|---|---|
| 1 | `@RetryOnTransientFailure(maxAttempts, backoffMs, retryOn)` | `RetryOnTransientFailureAspect` — **@Around** | Повтор операції при тимчасовому збої БД (deadlock, lock timeout, обрив з'єднання — `TransientDataAccessException`) | лог `[retry] ... retrying in 100 ms`; повторний `proceed()`; після останньої спроби виняток пробрасується | `BookService.create`, `BookService.update` |
| 2 | `@NormalizeText(blankToNull)` | `NormalizeTextAspect` — **@Around** | Нормалізація тексту з UI/API: strip, кілька пробілів/табів/NBSP -> один пробіл, `"   "` -> `null` | **змінює аргументи** (`proceed(normalizedArgs)`) -> у БД зберігається `"Clean Code"` замість `"  Clean   Code "`; лог `[normalize]` | `BookService.create`, `BookService.update` (працює і для REST, і для Thymeleaf-UI) |
| 3 | `@RateLimited(permits, windowSeconds)` | `RateLimitedAspect` — **@Before** | Обмеження частоти викликів на користувача (fixed window, ключ = метод + username) | **кидає виняток** `RateLimitExceededException` -> HTTP **429** + `Retry-After`; лог `[rate-limit]` | `AdminController.stats` (5/хв), `BookController.create` (30/хв) |

Жодна з них не є копією логування чи контролю доступу з лекції.

### Pointcut
Кожен аспект використовує `@annotation(повна.назва.Анотації)`, наприклад
`@Around("@annotation(com.example.library.aop.RetryOnTransientFailure)")`.
Анотації мають `@Retention(RUNTIME)` і `@Target(METHOD)`.

Атрибути анотації advice читає через `AopAnnotations.find(...)`, а не через прив'язку
`@annotation(retry)` + параметр. Причина знайдена під час роботи: коли зовнішній `@Around`
(NormalizeText) викликає `proceed(args)`, Spring клонує `MethodInvocation`, і прив'язка параметрів
pointcut-у для внутрішнього аспекту губиться — `IllegalStateException: JoinPointMatch was NOT bound in invocation`.

### Порядок аспектів
`NormalizeText` (`HIGHEST_PRECEDENCE`) -> `RetryOnTransientFailure` (`HIGHEST_PRECEDENCE + 10`) -> `@Transactional` (`LOWEST_PRECEDENCE`).
Retry знаходиться **зовні** транзакції, тому кожна спроба йде в новій транзакції, а не в уже «rollback-only».

## Self-invocation (навмисна демонстрація)
`BookService.createViaSelfInvocation(dto)` всередині викликає `create(dto)`, тобто `this.create(dto)`.
Виклик іде напряму в об'єкт, **оминаючи Spring-проксі**, тож `@NormalizeText` і `@RetryOnTransientFailure`
на `create` **не спрацьовують** — без жодної помилки чи попередження.

| Шлях виклику | Ендпоінт | Результат |
|---|---|---|
| з іншого біна (контролер -> проксі -> `create`) | `POST /api/demo/aop/books/external` | `title = "Clean Code"` — аспект спрацював |
| `this.create(...)` всередині BookService | `POST /api/demo/aop/books/self-invocation` | `title = "   Clean     Code  "` — аспект мовчки пропущено, статус 201 |

Це свідомий задум вправи, **не баг, який треба виправляти**. (Як виправили б у реальному коді:
винести метод в інший бін, інжектити self-proxy через `@Lazy`, або `AopContext.currentProxy()`.)

## Тести (`mvn clean verify`)
Docker/Keycloak/БД не потрібні.

| Тест | Що перевіряє | Spring-контекст |
|---|---|---|
| `RetryOnTransientFailureAspectTest` | mock `ProceedingJoinPoint`: успіх з 1-ї спроби (proceed 1 раз, без паузи); збій -> повтор -> успіх; вичерпання спроб (proceed 3 рази, паузи 100/200 мс); НЕ-тимчасовий виняток — без повтору; кастомні атрибути; maxAttempts=0; переривання потоку | **ні** |
| `NormalizeTextAspectTest` | mock `ProceedingJoinPoint` + `ArgumentCaptor`: `proceed(...)` викликано саме з нормалізованими аргументами, `proceed()` без аргументів — ніколи; DTO, String-параметри, `blankToNull=false`, вже чисті дані | **ні** |
| `RateLimitedAspectTest` | mock `JoinPoint` + керований `Clock` (без `Thread.sleep`): у межах ліміту — ок; понад ліміт — виняток з правильним `Retry-After`; нове вікно скидає лічильник; окремі ліміти для різних користувачів; anonymous | **ні** |
| `AopProxyAndSelfInvocationTest` | мінімальний `@SpringJUnitConfig` (НЕ `@SpringBootTest`): біни — проксі; зовнішній виклик -> normalize/retry/rate-limit спрацьовують; **self-invocation -> normalize і retry НЕ спрацьовують** | мінімальний, без БД |
| `GlobalExceptionHandlerTest.handleRateLimit_*` | 429 + `Retry-After` | ні |
| `AopDemoControllerTest` | демо-ендпоінти викликають потрібні методи сервісу | ні |

## Відповідність сценаріям методички
| Сценарій | Де |
|---|---|
| Анотація пов'язана з аспектом (`RUNTIME`, `@Target`, `@annotation(...)`) | анотації в `aop/`; `AopProxyAndSelfInvocationTest.annotations_haveRuntimeRetentionAndMethodTarget`, `beansWithAnnotatedMethods_areWrappedInAopProxy` |
| Поведінка advice помітна (лог / виняток / зміна значення) | retry — лог + повтор; normalize — змінені дані; rate-limit — виняток/429 |
| Зовнішній виклик активує аспект | `create_calledFromOutside_*`, `stats_calledFromOutside_*`; Postman `/external` |
| Self-invocation НЕ активує аспект | `create_calledViaSelfInvocation_*`; Postman `/self-invocation` |
| Advice перевіряється unit-тестом з mock `ProceedingJoinPoint`, без Spring | `RetryOnTransientFailureAspectTest`, `NormalizeTextAspectTest` (+ `RateLimitedAspectTest` з mock `JoinPoint`) |

## Ручна перевірка
Docker + Keycloak + `mvn spring-boot:run`, Postman — папка **«7. Lab 4 - AOP»**:
1. `POST /api/books` з «брудним» текстом -> у відповіді `"Clean Code"`; у консолі `[normalize] BookService.create(..): normalized 3 text value(s)`.
2. `/external` vs `/self-invocation` — різний результат при однаковому тілі.
3. `GET /api/admin/stats` 6-й раз за хвилину -> 429 + `Retry-After`; у консолі `[rate-limit] user 'admin' exceeded ...`.
   Після цього stats для admin недоступні ~60 с.
