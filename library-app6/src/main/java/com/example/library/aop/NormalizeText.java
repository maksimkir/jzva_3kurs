package com.example.library.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ЛР4, анотація 2 — нормалізація текстового вводу перед збереженням.
 * <p>
 * Наскрізна задача: дані приходять з UI-форми і з REST API з «сміттям» —
 * пробіли на краях, подвійні пробіли, табуляції, NBSP, порожні рядки замість null.
 * Через це "Clean Code" і " Clean  Code " — різні записи в БД, а пошук/унікальність ламаються.
 * <p>
 * {@link NormalizeTextAspect} ({@code @Around}) ЗМІНЮЄ АРГУМЕНТИ методу:
 * <ul>
 *   <li>String-параметри — замінюються нормалізованими значеннями через {@code proceed(args)};</li>
 *   <li>DTO-параметри (класи застосунку) — нормалізуються всі їхні String-властивості.</li>
 * </ul>
 * Нормалізація: strip() + послідовність горизонтальних пробілів -> один пробіл
 * (переноси рядків в описі зберігаються) + порожній рядок -> null (якщо {@link #blankToNull()}).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NormalizeText {

    /** Чи перетворювати рядок, що після нормалізації став порожнім, на null. */
    boolean blankToNull() default true;
}
