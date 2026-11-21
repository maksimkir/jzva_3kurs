package com.example.library.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeControllerTest {

    private final MeController controller = new MeController();

    @Test
    void me_userWithRoles_returnsNameAndSortedAuthorities() {
        // Arrange
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        // Act
        Map<String, Object> result = controller.me(auth);

        // Assert
        assertThat(result.get("name")).isEqualTo("admin");
        assertThat(result.get("authorities")).isEqualTo(List.of("ROLE_ADMIN", "ROLE_USER"));
    }

    @Test
    void me_userWithoutAuthorities_returnsEmptyAuthorityList() {
        // Arrange
        Authentication auth = new UsernamePasswordAuthenticationToken("guest", "n/a", List.of());

        // Act
        Map<String, Object> result = controller.me(auth);

        // Assert
        assertThat(result.get("name")).isEqualTo("guest");
        assertThat(result.get("authorities")).isEqualTo(List.of());
    }
}
