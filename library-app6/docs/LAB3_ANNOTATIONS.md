# Лабораторна №3 — Кастомні анотації

| Тип | Анотація | Механізм | Де |
|---|---|---|---|
| 1. Bean Validation | `@ValidIsbnAndYear` (рівень класу) | `@Constraint(validatedBy = IsbnAndYearValidator.class)` | `validation/`, застосовано до `BookDto` |
| 2. Композитна | `@PostCreated` | meta-анотація: `@RequestMapping(method = POST)` (= `@PostMapping`) + `@ResponseStatus(CREATED)`, `@AliasFor` | `annotation/PostCreated`, застосовано в `BookController.create` |
| 3. Резолвер аргументів | `@CurrentUsername` | `HandlerMethodArgumentResolver` + `WebMvcConfigurer.addArgumentResolvers` | `annotation/CurrentUsername`, `web/CurrentUsernameArgumentResolver`, `config/WebMvcConfig`, `ProfileController` |

## Правило @ValidIsbnAndYear
Якщо вказано ISBN: це ISBN-13 (13 цифр, дефіси дозволені, префікс 978/979, правильна контрольна цифра)
і обов'язковий `publishedYear` >= 1967. Без ISBN — перевірка не діє. Повідомлення прив'язані до полів `isbn` / `publishedYear`.

## Запуск тестів
```
mvn clean verify
```
Нові тести: `IsbnAndYearValidatorTest`, `BookControllerValidationRequestTest`, `PostCreatedTest`,
`CurrentUsernameArgumentResolverTest`, `ProfileControllerTest`, `WebMvcConfigTest`.

## Ручна перевірка (потрібні Docker + Keycloak + `mvn spring-boot:run`)
* UI: http://localhost:8080/books/new — ISBN `9780132350885` (неправильна контрольна цифра) -> помилка біля поля ISBN.
* Postman, папка «6. Lab 3 - Custom annotations».

## Відповідність Acceptance Criteria
| AC | Тест |
|---|---|
| 1 | `IsbnAndYearValidatorTest.annotation_isClassLevelConstraintWithValidatedBy`, `..._beanValidationInvokesValidatorAutomatically` |
| 2 | `validate_validIsbnAndYear_hasNoViolation`, `..._WithHyphens...`, `..._noIsbn...`, `..._yearExactlyAtMinimum...` |
| 3 | `validate_wrongCheckDigit_...`, `..._wrongLength_...`, `..._isbnWithoutYear_...`, `BookControllerValidationRequestTest` |
| 4, 5 | `PostCreatedTest` (композитна vs `@PostMapping`+`@ResponseStatus`: статус, тіло, 405, merged-метадані; реальний `BookController`) |
| 6 | `CurrentUsernameArgumentResolverTest.supportsParameter_*` |
| 7 | `CurrentUsernameArgumentResolverTest.resolveArgument_*` |
| 8 | `ProfileControllerTest.greeting_realRequestWithPrincipal_resolvedValueIsUsedInResponseBody` |
