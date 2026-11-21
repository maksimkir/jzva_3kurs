package com.example.library.controller;

import com.example.library.annotation.PostCreated;
import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService service;

    @GetMapping
    public List<BookDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public BookDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostCreated // = @PostMapping + @ResponseStatus(CREATED)
    public BookDto create(@Valid @RequestBody BookDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public BookDto update(@PathVariable Long id, @Valid @RequestBody BookDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id); // @PreAuthorize("hasRole('ADMIN')") всередині сервісу
    }
}
