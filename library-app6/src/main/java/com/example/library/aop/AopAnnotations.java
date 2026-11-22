package com.example.library.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Дістає анотацію методу, на якому спрацював advice.
 * <p>
 * Чому не {@code @Around("@annotation(retry)")} з параметром-анотацією: якщо зовнішній @Around-аспект
 * викликає {@code proceed(args)} (як NormalizeTextAspect) або {@code proceed()} кілька разів (як retry),
 * Spring AOP створює клон MethodInvocation, і прив'язка аргументів pointcut-у для внутрішніх аспектів
 * губиться ("JoinPointMatch was NOT bound in invocation"). Тому pointcut лише фільтрує
 * ({@code @annotation(повна.назва.Анотації)}), а значення атрибутів читаються тут.
 */
final class AopAnnotations {

    private AopAnnotations() {
    }

    static <A extends Annotation> A find(JoinPoint joinPoint, Class<A> type) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, type);
        if (annotation == null && joinPoint.getTarget() != null) {
            Method targetMethod = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, type);
        }
        if (annotation == null) {
            throw new IllegalStateException("@" + type.getSimpleName() + " not found on " + method);
        }
        return annotation;
    }
}
