package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.exception.GlobalExceptionHandler;
import com.example.library.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @ValidIsbnAndYear через реальний HTTP-запит (MockMvc standalone, без Spring-контексту). */
@ExtendWith(MockitoExtension.class)
class BookControllerValidationRequestTest {

    @Mock
    private BookService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String json(String isbn, String year) {
        return "{\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\""
                + (isbn == null ? "" : ",\"isbn\":\"" + isbn + "\"")
                + (year == null ? "" : ",\"publishedYear\":" + year) + "}";
    }

    @Test
    void create_wrongCheckDigit_returns400WithClearMessageOnIsbnField() throws Exception {
        // Arrange
        String body = json("9780132350885", "2008");

        // Act + Assert
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn", containsString("очікувалась 4")));
        verifyNoInteractions(service);
    }

    @Test
    void create_isbnWithoutYear_returns400WithMessageOnYearField() throws Exception {
        // Arrange
        String body = json("9780132350884", null);

        // Act + Assert
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.publishedYear", containsString("обов")));
        verifyNoInteractions(service);
    }

    @Test
    void create_validIsbnAndYear_returns201() throws Exception {
        // Arrange
        BookDto saved = new BookDto();
        saved.setId(1L);
        saved.setTitle("Clean Code");
        saved.setAuthor("Robert C. Martin");
        when(service.create(any(BookDto.class))).thenReturn(saved);
        String body = json("978-0-13-235088-4", "2008");

        // Act + Assert
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
