package com.example.library.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookDto {

    private Long id;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String author;

    @Size(max = 20)
    private String isbn;

    @Min(0)
    @Max(2100)
    private Integer publishedYear;

    @Size(max = 2000)
    private String description;
}
