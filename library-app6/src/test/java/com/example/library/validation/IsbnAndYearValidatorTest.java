package com.example.library.validation;

import com.example.library.dto.BookDto;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряємо constraint через справжній Bean Validation (Hibernate Validator) —
 * валідатор знаходиться самою бібліотекою за @Constraint(validatedBy = ...), без Spring-контексту.
 */
class IsbnAndYearValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static BookDto book(String isbn, Integer year) {
        BookDto d = new BookDto();
        d.setTitle("Clean Code");
        d.setAuthor("Robert C. Martin");
        d.setIsbn(isbn);
        d.setPublishedYear(year);
        return d;
    }

    /** Лише порушення нашого constraint-а (інші правила BookDto не враховуються). */
    private static Set<ConstraintViolation<BookDto>> ourViolations(BookDto dto) {
        return validator.validate(dto).stream()
                .filter(v -> v.getConstraintDescriptor().getAnnotation() instanceof ValidIsbnAndYear)
                .collect(Collectors.toSet());
    }

    private static String messageFor(Set<ConstraintViolation<BookDto>> violations, String property) {
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------ AC1

    @Test
    void annotation_isClassLevelConstraintWithValidatedBy() {
        // Arrange + Act
        Constraint constraint = ValidIsbnAndYear.class.getAnnotation(Constraint.class);
        Target target = ValidIsbnAndYear.class.getAnnotation(Target.class);

        // Assert
        assertThat(constraint).isNotNull();
        assertThat(constraint.validatedBy()).containsExactly(IsbnAndYearValidator.class);
        assertThat(Arrays.asList(target.value())).contains(ElementType.TYPE);
        assertThat(BookDto.class.isAnnotationPresent(ValidIsbnAndYear.class)).isTrue();
    }

    @Test
    void validate_invalidBook_beanValidationInvokesValidatorAutomatically() {
        // Arrange
        BookDto dto = book("9780132350885", 2008);

        // Act (валідатор жодного разу не створюється вручну)
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).hasSize(1);
    }

    // ------------------------------------------------------------ AC2: валідні дані

    @Test
    void validate_validIsbnAndYear_hasNoViolation() {
        // Arrange
        BookDto dto = book("9780132350884", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_validIsbnWithHyphensAndSpaces_hasNoViolation() {
        // Arrange
        BookDto dto = book("978-0-13-235088-4", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_noIsbn_hasNoViolationEvenWithoutYear() {
        // Arrange
        BookDto dto = book(null, null);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_blankIsbn_hasNoViolation() {
        // Arrange
        BookDto dto = book("   ", null);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_yearExactlyAtMinimum_hasNoViolation() {
        // Arrange (межове значення: 1967)
        BookDto dto = book("9780132350884", 1967);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    // ------------------------------------------------------------ AC3: порушення з чіткими повідомленнями

    @Test
    void validate_wrongCheckDigit_reportsExpectedAndActualDigit() {
        // Arrange (правильна остання цифра — 4)
        BookDto dto = book("9780132350885", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        String message = messageFor(violations, "isbn");
        assertThat(message).contains("контрольна цифра").contains("очікувалась 4").contains("вказано 5");
    }

    @Test
    void validate_wrongLength_reportsActualLength() {
        // Arrange (12 цифр)
        BookDto dto = book("978013235088", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        String message = messageFor(violations, "isbn");
        assertThat(message).contains("рівно 13 цифр").contains("знайдено 12");
    }

    @Test
    void validate_nonDigitCharacters_reportsDigitsOnly() {
        // Arrange
        BookDto dto = book("97801323508X4", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(messageFor(violations, "isbn")).contains("лише цифри");
    }

    @Test
    void validate_wrongPrefix_reportsAllowedPrefixes() {
        // Arrange (13 цифр, але префікс 123)
        BookDto dto = book("1234567890123", 2008);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(messageFor(violations, "isbn")).contains("978 або 979");
    }

    @Test
    void validate_isbnWithoutYear_reportsYearRequired() {
        // Arrange
        BookDto dto = book("9780132350884", null);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(messageFor(violations, "publishedYear")).contains("обов’язковий");
        assertThat(messageFor(violations, "isbn")).isNull();
    }

    @Test
    void validate_yearBeforeIsbnStandard_reportsMinimumYear() {
        // Arrange
        BookDto dto = book("9780132350884", 1950);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(messageFor(violations, "publishedYear")).contains("1950").contains("1967");
    }

    @Test
    void validate_isbnAndYearBothInvalid_reportsTwoViolationsOnDifferentFields() {
        // Arrange
        BookDto dto = book("9780132350885", 1900);

        // Act
        Set<ConstraintViolation<BookDto>> violations = ourViolations(dto);

        // Assert
        assertThat(violations).hasSize(2);
        assertThat(messageFor(violations, "isbn")).isNotNull();
        assertThat(messageFor(violations, "publishedYear")).isNotNull();
    }

    @Test
    void isValid_nullDto_returnsTrue() {
        // Arrange
        IsbnAndYearValidator validatorUnderTest = new IsbnAndYearValidator();

        // Act
        boolean result = validatorUnderTest.isValid(null, null);

        // Assert
        assertThat(result).isTrue();
    }
}
