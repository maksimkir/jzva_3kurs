package com.example.library.web;

import com.example.library.annotation.CurrentUsername;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUsernameArgumentResolverTest {

    /** Зразок методу контролера з різними варіантами параметрів. */
    static class Sample {
        @SuppressWarnings("unused")
        void handler(@CurrentUsername String user,
                     String plain,
                     @CurrentUsername Integer wrongType,
                     @CurrentUsername(required = false) String optionalUser) {
        }
    }

    private final CurrentUsernameArgumentResolver resolver = new CurrentUsernameArgumentResolver();

    private static MethodParameter param(int index) throws Exception {
        Method method = Sample.class.getDeclaredMethod("handler",
                String.class, String.class, Integer.class, String.class);
        return new MethodParameter(method, index);
    }

    private static ServletWebRequest requestWithPrincipal(String name) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (name != null) {
            Principal principal = () -> name;
            request.setUserPrincipal(principal);
        }
        return new ServletWebRequest(request);
    }

    // ------------------------------------------------------------ AC6: supportsParameter

    @Test
    void supportsParameter_stringWithAnnotation_returnsTrue() throws Exception {
        // Arrange
        MethodParameter annotated = param(0);

        // Act
        boolean result = resolver.supportsParameter(annotated);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void supportsParameter_parameterWithoutAnnotation_returnsFalse() throws Exception {
        // Arrange
        MethodParameter plain = param(1);

        // Act
        boolean result = resolver.supportsParameter(plain);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void supportsParameter_annotatedButNotString_returnsFalse() throws Exception {
        // Arrange
        MethodParameter wrongType = param(2);

        // Act
        boolean result = resolver.supportsParameter(wrongType);

        // Assert
        assertThat(result).isFalse();
    }

    // ------------------------------------------------------------ AC7: resolveArgument

    @Test
    void resolveArgument_authenticatedUser_returnsUsernameAsString() throws Exception {
        // Arrange
        ServletWebRequest request = requestWithPrincipal("admin");

        // Act
        Object result = resolver.resolveArgument(param(0), null, request, null);

        // Assert
        assertThat(result).isInstanceOf(String.class).isEqualTo("admin");
    }

    @Test
    void resolveArgument_differentUser_returnsThatUsername() throws Exception {
        // Arrange
        ServletWebRequest request = requestWithPrincipal("alice");

        // Act
        Object result = resolver.resolveArgument(param(0), null, request, null);

        // Assert
        assertThat(result).isEqualTo("alice");
    }

    @Test
    void resolveArgument_noUserAndRequired_throwsAuthenticationException() throws Exception {
        // Arrange
        ServletWebRequest request = requestWithPrincipal(null);
        MethodParameter required = param(0);

        // Act + Assert
        assertThatThrownBy(() -> resolver.resolveArgument(required, null, request, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void resolveArgument_noUserAndNotRequired_returnsNull() throws Exception {
        // Arrange
        ServletWebRequest request = requestWithPrincipal(null);

        // Act
        Object result = resolver.resolveArgument(param(3), null, request, null);

        // Assert
        assertThat(result).isNull();
    }
}
