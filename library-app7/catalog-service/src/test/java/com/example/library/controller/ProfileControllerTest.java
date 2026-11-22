package com.example.library.controller;

import com.example.library.web.CurrentUsernameArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController())
                .setCustomArgumentResolvers(new CurrentUsernameArgumentResolver())
                .build();
    }

    @Test
    void greeting_directCall_usesResolvedUsernameInBody() {
        // Arrange
        ProfileController controller = new ProfileController();

        // Act
        Map<String, String> result = controller.greeting("alice");

        // Assert
        assertThat(result.get("username")).isEqualTo("alice");
        assertThat(result.get("greeting")).contains("alice");
    }

    @Test
    void greeting_realRequestWithPrincipal_resolvedValueIsUsedInResponseBody() throws Exception {
        // Arrange
        Principal principal = () -> "admin";

        // Act + Assert
        mockMvc.perform(get("/api/me/greeting").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.greeting", containsString("admin")));
    }

    @Test
    void greeting_realRequestsFromTwoUsers_eachGetsOwnName() throws Exception {
        // Arrange
        Principal first = () -> "alice";
        Principal second = () -> "bob";

        // Act + Assert
        mockMvc.perform(get("/api/me/greeting").principal(first))
                .andExpect(jsonPath("$.username").value("alice"));
        mockMvc.perform(get("/api/me/greeting").principal(second))
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void greeting_realRequestWithoutUser_failsWithAuthenticationException() {
        // Arrange (principal не задано)

        // Act + Assert
        assertThatThrownBy(() -> mockMvc.perform(get("/api/me/greeting")))
                .hasRootCauseInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
