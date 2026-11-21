package com.example.library.validation;

import com.example.library.dto.BookDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnAndYearValidator implements ConstraintValidator<ValidIsbnAndYear, BookDto> {

    private int minYear;

    @Override
    public void initialize(ValidIsbnAndYear annotation) {
        this.minYear = annotation.minYear();
    }

    @Override
    public boolean isValid(BookDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }
        String rawIsbn = dto.getIsbn();
        if (rawIsbn == null || rawIsbn.isBlank()) {
            return true; // ISBN необов'язковий
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        String isbnError = checkIsbn(rawIsbn.replace("-", "").replace(" ", ""));
        if (isbnError != null) {
            addViolation(context, "isbn", isbnError);
            valid = false;
        }

        Integer year = dto.getPublishedYear();
        if (year == null) {
            addViolation(context, "publishedYear", "Рік видання обов’язковий, якщо вказано ISBN");
            valid = false;
        } else if (year < minYear) {
            addViolation(context, "publishedYear", "Рік видання " + year
                    + " неможливий для книги з ISBN: стандарт ISBN з’явився у " + minYear + " році");
            valid = false;
        }
        return valid;
    }

    /** Повертає текст помилки або null, якщо ISBN-13 коректний. */
    static String checkIsbn(String digits) {
        if (!digits.matches("\\d+")) {
            return "ISBN має містити лише цифри та дефіси";
        }
        if (digits.length() != 13) {
            return "ISBN має містити рівно 13 цифр (дефіси дозволені), але знайдено " + digits.length();
        }
        if (!digits.startsWith("978") && !digits.startsWith("979")) {
            return "ISBN-13 має починатися з 978 або 979";
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = digits.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : 3 * d;
        }
        int expected = (10 - sum % 10) % 10;
        int actual = digits.charAt(12) - '0';
        if (expected != actual) {
            return "Неправильна контрольна цифра ISBN-13: очікувалась " + expected + ", а вказано " + actual;
        }
        return null;
    }

    private static void addViolation(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
