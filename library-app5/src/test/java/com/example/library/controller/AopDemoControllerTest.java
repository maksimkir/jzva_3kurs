package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AopDemoControllerTest {

    @Mock
    private BookService service;

    @InjectMocks
    private AopDemoController controller;

    @Test
    void external_delegatesToProxiedCreate() {
        // Arrange
        BookDto dto = new BookDto();
        when(service.create(dto)).thenReturn(dto);

        // Act
        BookDto result = controller.external(dto);

        // Assert
        assertThat(result).isSameAs(dto);
        verify(service).create(dto);
        verifyNoMoreInteractions(service);
    }

    @Test
    void selfInvocation_delegatesToSelfInvokingMethod() {
        // Arrange
        BookDto dto = new BookDto();
        when(service.createViaSelfInvocation(dto)).thenReturn(dto);

        // Act
        BookDto result = controller.selfInvocation(dto);

        // Assert
        assertThat(result).isSameAs(dto);
        verify(service).createViaSelfInvocation(dto);
        verifyNoMoreInteractions(service);
    }
}
