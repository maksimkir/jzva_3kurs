package com.example.library.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ТИП 2 — композитна (meta) анотація.
 * Поєднує те, що в REST-контролерах майже завжди йде разом при створенні ресурсу:
 *   @PostMapping (= @RequestMapping(method = POST))  +  @ResponseStatus(HttpStatus.CREATED).
 * Той самий патерн, що й @RestController (= @Controller + @ResponseBody).
 * Атрибути path/consumes/produces прокидаються в @RequestMapping через @AliasFor.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.POST)
@ResponseStatus(HttpStatus.CREATED)
public @interface PostCreated {

    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};

    @AliasFor(annotation = RequestMapping.class)
    String[] consumes() default {};

    @AliasFor(annotation = RequestMapping.class)
    String[] produces() default {};
}
