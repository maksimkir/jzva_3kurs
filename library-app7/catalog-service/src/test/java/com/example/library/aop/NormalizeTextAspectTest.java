package com.example.library.aop;

import com.example.library.dto.BookDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тест advice без Spring: перевіряємо, що proceed() викликається саме з ЗМІНЕНИМИ (нормалізованими) аргументами.
 */
@ExtendWith(MockitoExtension.class)
class NormalizeTextAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private final NormalizeTextAspect aspect = new NormalizeTextAspect();

    private Object[] proceedAndCaptureArgs(Object[] args, String annotationSample) throws Throwable {
        when(joinPoint.getArgs()).thenReturn(args);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed(any(Object[].class))).thenReturn("result");

        Object result = aspect.normalize(joinPoint, annotation(annotationSample));

        assertThat(result).isEqualTo("result");
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(captor.capture());
        verify(joinPoint, never()).proceed();
        return captor.getValue();
    }

    @Test
    void normalize_dtoWithMessyText_proceedsWithCleanedDto() throws Throwable {
        // Arrange
        BookDto dto = new BookDto();
        dto.setTitle("   Clean \t  Code  ");
        dto.setAuthor("Robert  C.  Martin");
        dto.setIsbn("   ");
        dto.setDescription("  Line one  \n   line two ");
        dto.setPublishedYear(2008);

        // Act
        Object[] passed = proceedAndCaptureArgs(new Object[]{dto}, "defaults");

        // Assert
        BookDto cleaned = (BookDto) passed[0];
        assertThat(cleaned.getTitle()).isEqualTo("Clean Code");
        assertThat(cleaned.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(cleaned.getIsbn()).isNull();
        assertThat(cleaned.getDescription()).isEqualTo("Line one\nline two");
        assertThat(cleaned.getPublishedYear()).isEqualTo(2008);
    }

    @Test
    void normalize_stringAndNonTextArgs_replacesOnlyStrings() throws Throwable {
        // Arrange
        Object[] original = {7L, "  hello   world ", null};

        // Act
        Object[] passed = proceedAndCaptureArgs(original, "defaults");

        // Assert
        assertThat(passed).containsExactly(7L, "hello world", null);
        assertThat(original[1]).as("original array is not mutated").isEqualTo("  hello   world ");
    }

    @Test
    void normalize_blankToNullDisabled_keepsEmptyString() throws Throwable {
        // Arrange
        Object[] original = {"    "};

        // Act
        Object[] passed = proceedAndCaptureArgs(original, "keepBlank");

        // Assert
        assertThat(passed).containsExactly("");
    }

    @Test
    void normalize_alreadyCleanInput_proceedsWithEqualArgs() throws Throwable {
        // Arrange
        BookDto dto = new BookDto();
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");

        // Act
        Object[] passed = proceedAndCaptureArgs(new Object[]{5L, dto}, "defaults");

        // Assert
        assertThat(passed[0]).isEqualTo(5L);
        assertThat(((BookDto) passed[1]).getTitle()).isEqualTo("Clean Code");
        assertThat(((BookDto) passed[1]).getAuthor()).isEqualTo("Robert C. Martin");
    }

    @Test
    void normalize_staticHelper_nullStaysNull() {
        // Act + Assert
        assertThat(NormalizeTextAspect.normalize(null, true)).isNull();
        assertThat(NormalizeTextAspect.normalize("\t a   b \t", true)).isEqualTo("a b");
    }

    private static NormalizeText annotation(String method) throws NoSuchMethodException {
        return Samples.class.getDeclaredMethod(method).getAnnotation(NormalizeText.class);
    }

    @SuppressWarnings("unused")
    private static class Samples {
        @NormalizeText
        void defaults() {
        }

        @NormalizeText(blankToNull = false)
        void keepBlank() {
        }
    }
}
