package com.example.bookinfo;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-контракт, який читає catalog-service: JSON полів RatingInfo/AvailabilityInfo і 422 без ISBN. */
class BookInfoControllerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new BookInfoController(new BookInfoService(new LatencySimulator(0))))
            .build();

    @Test
    void rating_withIsbn_returnsRatingJson() throws Exception {
        mvc.perform(get("/api/book-info/rating").param("isbn", "9780132350884"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.average").isNumber())
                .andExpect(jsonPath("$.votes").isNumber());
    }

    @Test
    void rating_withoutIsbn_returns422WithExplanation() throws Exception {
        mvc.perform(get("/api/book-info/rating"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("ISBN")));
    }

    @Test
    void availability_returnsAvailabilityJson() throws Exception {
        mvc.perform(get("/api/book-info/availability").param("title", "Clean Code").param("author", "Robert C. Martin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.partnerLibraries").isNumber());
    }

    @Test
    void availability_missingTitle_returns400() throws Exception {
        mvc.perform(get("/api/book-info/availability").param("author", "X"))
                .andExpect(status().isBadRequest());
    }
}
