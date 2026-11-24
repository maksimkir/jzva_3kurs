package com.example.library.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ТИП 3 — анотація параметра контролера, яку резолвить HandlerMethodArgumentResolver.
 * Підставляє ім'я поточного автентифікованого користувача (логін з Keycloak:
 * preferred_username — і для JWT, і для OIDC-сесії) без повторення логіки в кожному методі.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUsername {

    /** Якщо true і користувач не автентифікований — кидається AuthenticationCredentialsNotFoundException. */
    boolean required() default true;
}
