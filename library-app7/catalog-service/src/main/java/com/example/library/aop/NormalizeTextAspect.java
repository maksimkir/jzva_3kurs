package com.example.library.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Advice: {@code @Around}. Нормалізує текстові аргументи і передає їх далі через
 * {@code proceed(normalizedArgs)} — тобто метод отримує вже «чисті» дані.
 * Виконується найпершим (найвищий пріоритет), тому всі інші аспекти та сам метод бачать нормалізовані значення.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NormalizeTextAspect {

    private static final String APP_PACKAGE = "com.example.library";
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("\\h+");

    @Around("@annotation(com.example.library.aop.NormalizeText)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return normalize(joinPoint, AopAnnotations.find(joinPoint, NormalizeText.class));
    }

    Object normalize(ProceedingJoinPoint joinPoint, NormalizeText normalize) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Object[] normalizedArgs = args.clone();
        int changed = 0;

        for (int i = 0; i < normalizedArgs.length; i++) {
            Object arg = normalizedArgs[i];
            if (arg instanceof String text) {
                String value = normalize(text, normalize.blankToNull());
                if (!Objects.equals(text, value)) {
                    normalizedArgs[i] = value;
                    changed++;
                }
            } else if (isApplicationBean(arg)) {
                changed += normalizeProperties(arg, normalize.blankToNull());
            }
        }

        if (changed > 0) {
            log.info("[normalize] {}: normalized {} text value(s)", joinPoint.getSignature().toShortString(), changed);
        }
        return joinPoint.proceed(normalizedArgs);
    }

    /** strip + схлопування горизонтальних пробілів (NBSP, таби) в один пробіл; порожнє -> null. */
    static String normalize(String value, boolean blankToNull) {
        if (value == null) {
            return null;
        }
        String result = HORIZONTAL_WHITESPACE.matcher(value).replaceAll(" ").strip();
        result = result.replaceAll(" *\\R *", "\n"); // пробіли біля переносів рядка
        return (blankToNull && result.isEmpty()) ? null : result;
    }

    private static boolean isApplicationBean(Object arg) {
        return arg != null && arg.getClass().getName().startsWith(APP_PACKAGE);
    }

    private static int normalizeProperties(Object bean, boolean blankToNull) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);
        int changed = 0;
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if (pd.getPropertyType() == String.class && wrapper.isReadableProperty(name) && wrapper.isWritableProperty(name)) {
                String oldValue = (String) wrapper.getPropertyValue(name);
                String newValue = normalize(oldValue, blankToNull);
                if (!Objects.equals(oldValue, newValue)) {
                    wrapper.setPropertyValue(name, newValue);
                    changed++;
                }
            }
        }
        return changed;
    }
}
