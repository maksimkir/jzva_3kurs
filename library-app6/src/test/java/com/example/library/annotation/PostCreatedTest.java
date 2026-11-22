package com.example.library.annotation;

import com.example.library.controller.BookController;
import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Порівнюємо композитну @PostCreated з двома оригінальними анотаціями,
 * застосованими окремо (@PostMapping + @ResponseStatus(CREATED)).
 */
@ExtendWith(MockitoExtension.class)
class PostCreatedTest {

    @RestController
    static class PlainController {
        @PostMapping("/items")
        @ResponseStatus(HttpStatus.CREATED)
        public String create() {
            return "created";
        }
    }

    @RestController
    static class ComposedController {
        @PostCreated("/items")
        public String create() {
            return "created";
        }
    }

    @Mock
    private BookService bookService;

    private static MockMvc mvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ------------------------------------------------------------ AC4: однакова поведінка

    @Test
    void composedAndPlain_post_returnSameStatus201AndSameBody() throws Exception {
        // Arrange
        MockMvc plain = mvcFor(new PlainController());
        MockMvc composed = mvcFor(new ComposedController());

        // Act
        MvcResult plainResult = plain.perform(post("/items")).andReturn();
        MvcResult composedResult = composed.perform(post("/items")).andReturn();

        // Assert
        assertThat(composedResult.getResponse().getStatus()).isEqualTo(201);
        assertThat(composedResult.getResponse().getStatus()).isEqualTo(plainResult.getResponse().getStatus());
        assertThat(composedResult.getResponse().getContentAsString())
                .isEqualTo(plainResult.getResponse().getContentAsString())
                .isEqualTo("created");
    }

    @Test
    void composedAndPlain_getOnPostOnlyPath_bothReturn405() throws Exception {
        // Arrange
        MockMvc plain = mvcFor(new PlainController());
        MockMvc composed = mvcFor(new ComposedController());

        // Act
        int plainStatus = plain.perform(get("/items")).andReturn().getResponse().getStatus();
        int composedStatus = composed.perform(get("/items")).andReturn().getResponse().getStatus();

        // Assert
        assertThat(composedStatus).isEqualTo(405).isEqualTo(plainStatus);
    }

    @Test
    void composed_mergedMetadata_equalsOriginalAnnotations() throws Exception {
        // Arrange
        Method plainMethod = PlainController.class.getMethod("create");
        Method composedMethod = ComposedController.class.getMethod("create");

        // Act
        RequestMapping plainMapping = AnnotatedElementUtils.findMergedAnnotation(plainMethod, RequestMapping.class);
        RequestMapping composedMapping = AnnotatedElementUtils.findMergedAnnotation(composedMethod, RequestMapping.class);
        ResponseStatus plainStatus = AnnotatedElementUtils.findMergedAnnotation(plainMethod, ResponseStatus.class);
        ResponseStatus composedStatus = AnnotatedElementUtils.findMergedAnnotation(composedMethod, ResponseStatus.class);

        // Assert
        assertThat(composedMapping.method()).containsExactly(RequestMethod.POST).isEqualTo(plainMapping.method());
        assertThat(composedMapping.path()).containsExactly("/items").isEqualTo(plainMapping.path());
        assertThat(composedStatus.code()).isEqualTo(HttpStatus.CREATED).isEqualTo(plainStatus.code());
    }

    // ------------------------------------------------------------ AC5: реальний ендпоінт

    @Test
    void bookController_createMethod_usesComposedAnnotationWithPostAndCreated() throws Exception {
        // Arrange
        Method create = BookController.class.getMethod("create", BookDto.class);

        // Act
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(create, RequestMapping.class);
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(create, ResponseStatus.class);

        // Assert
        assertThat(create.isAnnotationPresent(PostCreated.class)).isTrue();
        assertThat(mapping.method()).containsExactly(RequestMethod.POST);
        assertThat(responseStatus.code()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void bookController_postRequest_returns201AndDelegatesToService() throws Exception {
        // Arrange
        BookDto saved = new BookDto();
        saved.setId(10L);
        saved.setTitle("Clean Code");
        saved.setAuthor("Robert C. Martin");
        when(bookService.create(any(BookDto.class))).thenReturn(saved);
        MockMvc mockMvc = mvcFor(new BookController(bookService, org.mockito.Mockito.mock(com.example.library.report.BookReportService.class)));

        // Act + Assert
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<BookDto> captor = ArgumentCaptor.forClass(BookDto.class);
        verify(bookService).create(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Clean Code");
    }
}
