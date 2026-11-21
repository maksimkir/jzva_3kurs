package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookViewController {

    private final BookService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("books", service.findAll());
        return "books/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("book", new BookDto());
        return "books/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("book", service.findById(id));
        return "books/form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("book") BookDto book, BindingResult result) {
        if (result.hasErrors()) {
            return "books/form";
        }
        if (book.getId() == null) {
            service.create(book);
        } else {
            service.update(book.getId(), book);
        }
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id); // для USER -> AccessDeniedException -> 403
        return "redirect:/books";
    }
}
