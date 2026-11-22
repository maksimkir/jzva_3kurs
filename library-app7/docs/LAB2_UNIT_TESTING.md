# Лабораторна №2 — Unit-тестування

## Запуск
```
mvn clean verify
```
* `mvn test` — лише тести; `mvn clean verify` — тести + звіт JaCoCo + перевірка, що покриття рядків >= 60%.
* HTML-звіт покриття: `target/site/jacoco/index.html` (скріншот звіту — доказ для захисту).
* Docker, Keycloak та БД для тестів НЕ потрібні.
* Один клас: `mvn test "-Dtest=BookServiceTest"`.

## Що покрито
| Клас | Тест | Залежності (замоковані) |
|---|---|---|
| `BookService` (основна бізнес-логіка) | `BookServiceTest` | `BookRepository`, `BookMapper` |
| `BookController` | `BookControllerTest` | `BookService` |
| `BookViewController` | `BookViewControllerTest` | `BookService` |
| `AdminController` | `AdminControllerTest` | `BookService` |
| `MeController`, `HomeController` | `MeControllerTest`, `HomeControllerTest` | — |
| `BookMapper` (MapStruct) | `BookMapperTest` | — |
| `GlobalExceptionHandler`, `ResourceNotFoundException` | `GlobalExceptionHandlerTest`, `ResourceNotFoundExceptionTest` | — |
| `SecurityConfig` (мапінг ролей) | `SecurityConfigTest` | — |

## Відповідність Acceptance Criteria
| AC | Як виконано |
|----|-------------|
| 1 (happy path + assert) | Кожен публічний метод має тест зі звичайними даними: `findAll_booksExist_...`, `findById_existingId_...`, `create_validDto_...`, `update_existingId_...`, `delete_existingId_...`, `count_booksExist_...` |
| 2 (невалідні/межові/відмова) | `findById_unknownId_throws...`, `update_unknownId_throws...`, `delete_unknownId_throws...`, `create_repositoryFails_propagates...`, `findAll_noBooks_returnsEmptyList`, `count_emptyRepository_returnsZero` |
| 3 (@Mock/@InjectMocks, без Spring) | `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`; жодного `@SpringBootTest` |
| 4 (мутація) | `scripts/mutation-demo.ps1` — див. нижче |
| 5 (ізоляція) | Немає БД/мережі/файлів, немає `Thread.sleep`, час у тестах фіксований (`Instant.parse(...)`), тести незалежні |
| 6 (імена + AAA) | Імена `метод_сценарій_результат`, у тілі коментарі `// Arrange`, `// Act`, `// Assert` |
| 7 (verify/ArgumentCaptor) | `create_validDto_...` (ArgumentCaptor на `repository.save`), `update_existingId_...` (InOrder: `updateEntity` -> `save`), `delete_existingId_...` (`verify(repository).delete(existing)`), `*_never()` у негативних сценаріях |
| 8 (всі публічні методи) | `BookService`: findAll, findById, create, update, delete, count — у кожного є і AC1-, і AC2-тест |

## AC4: доказ, що тест справді перевіряє логіку
Автоматично (рекомендовано), з кореня проєкту:
```
powershell -ExecutionPolicy Bypass -File scripts\mutation-demo.ps1
```
Скрипт: 1) тести проходять; 2) у `BookService.delete` прибирається `repository.delete(...)` — `delete_existingId_deletesFoundEntity` ПАДАЄ (скріншот №1); 3) файл відновлюється, тести знову проходять (скріншот №2).

Вручну: у `BookService.delete` замініть `repository.delete(getEntity(id));` на `getEntity(id);`, запустіть `mvn test "-Dtest=BookServiceTest"` (FAIL), поверніть рядок, запустіть знову (PASS).
Ще мутації для демонстрації: у `count()` повернути `0`; у `update` прибрати `mapper.updateEntity(dto, book);`; у `getEntity` замінити `orElseThrow(...)` на `orElse(null)`.

## Нотатки
* `@PreAuthorize` у unit-тесті без Spring не виконується, тому перевіряється наявність анотації через reflection (`delete_methodSecurityAnnotation_requiresAdminRole`). Сама поведінка 403 показана в Postman-колекції з лаби №1.
* `lombok.config` додає `@Generated` до згенерованого Lombok-коду, щоб JaCoCo не рахував його у покритті.
* В `SecurityConfig` методи `jwtAuthenticationConverter()` і `oidcAuthoritiesMapper()` зроблено package-private, щоб їх можна було тестувати.
