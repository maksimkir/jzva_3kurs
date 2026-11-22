package com.example.library.controller;

import com.example.library.annotation.PostCreated;
import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ЛР4 — демонстрація self-invocation. Обидва ендпоінти зберігають книгу через BookService.create(...),
 * але різними шляхами:
 * <ul>
 *   <li>{@code /external} — виклик з іншого біна через проксі: @NormalizeText спрацьовує, пробіли прибрано;</li>
 *   <li>{@code /self-invocation} — create() викликається зсередини BookService (this.create):
 *       аспект НЕ спрацьовує, книга зберігається «брудною», жодної помилки.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/demo/aop/books")
@RequiredArgsConstructor
public class AopDemoController {

    private final BookService service;

    @PostCreated("/external")
    public BookDto external(@Valid @RequestBody BookDto dto) {
        return service.create(dto);
    }

    @PostCreated("/self-invocation")
    public BookDto selfInvocation(@Valid @RequestBody BookDto dto) {
        return service.createViaSelfInvocation(dto);
    }
}
