package com.example.library.async;

import com.example.library.controller.BookController;
import com.example.library.dto.BookDto;
import com.example.library.exception.GlobalExceptionHandler;
import com.example.library.external.AvailabilityInfo;
import com.example.library.external.RatingInfo;
import com.example.library.report.BookReportDto;
import com.example.library.report.BookReportService;
import com.example.library.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-рівень: переповнений пул -> 503 з «спробуйте пізніше», а НЕ 500; частковий звіт -> 200. */
class BookReportEndpointTest {

    private final BookReportService reportService = mock(BookReportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(mock(BookService.class), reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void report_executorOverloaded_returns503TryLater() throws Exception {
        // Arrange
        when(reportService.buildReport(1L)).thenThrow(new TaskRejectedException("Executor rejected task"));

        // Act + Assert
        mockMvc.perform(get("/api/books/1/report"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Спробуйте, будь ласка, пізніше")));
    }

    @Test
    void report_partialData_returns200WithFallbackFields() throws Exception {
        // Arrange
        BookDto book = new BookDto();
        book.setId(1L);
        book.setTitle("Clean Code");
        when(reportService.buildReport(1L)).thenReturn(new BookReportDto(book,
                RatingInfo.unavailable("тайм-аут"), AvailabilityInfo.of(2, 5), false, 510));

        // Act + Assert
        mockMvc.perform(get("/api/books/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.rating.available").value(false))
                .andExpect(jsonPath("$.availability.copies").value(5));
    }
}
