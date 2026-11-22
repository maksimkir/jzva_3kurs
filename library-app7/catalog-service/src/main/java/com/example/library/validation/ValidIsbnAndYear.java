package com.example.library.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ТИП 1 — Bean Validation constraint (рівень класу).
 * Бізнес-правило для книги: якщо вказано ISBN, то
 *  - це коректний ISBN-13 (13 цифр, префікс 978/979, правильна контрольна цифра);
 *  - рік видання обов'язковий і не раніше за {@link #minYear()} (стандарт ISBN з'явився у 1967).
 * Валідатор знаходиться Bean Validation автоматично через @Constraint(validatedBy = ...).
 */
@Documented
@Constraint(validatedBy = IsbnAndYearValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsbnAndYear {

    String message() default "Некоректні ISBN або рік видання";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Найраніший допустимий рік видання для книги з ISBN. */
    int minYear() default 1967;
}
