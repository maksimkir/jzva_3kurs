package com.example.library.mapper;

import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookMapper {

    BookDto toDto(Book book);

    List<BookDto> toDtoList(List<Book> books);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "announcedAt", ignore = true)
    Book toEntity(BookDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "announcedAt", ignore = true)
    void updateEntity(BookDto dto, @MappingTarget Book book);
}
