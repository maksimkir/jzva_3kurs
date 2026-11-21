package com.example.library.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    @Test
    void index_always_returnsIndexView() {
        // Arrange
        HomeController controller = new HomeController();

        // Act
        String view = controller.index();

        // Assert
        assertThat(view).isEqualTo("index");
    }
}
