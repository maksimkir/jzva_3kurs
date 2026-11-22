package com.example.library.config;

import com.example.library.web.CurrentUsernameArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    void addArgumentResolvers_registersCurrentUsernameResolver() {
        // Arrange
        CurrentUsernameArgumentResolver resolver = new CurrentUsernameArgumentResolver();
        WebMvcConfig config = new WebMvcConfig(resolver);
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        // Act
        config.addArgumentResolvers(resolvers);

        // Assert
        assertThat(resolvers).containsExactly(resolver);
    }
}
