package com.example.library.mapper;

import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тести MapStruct-маппера. Залежностей немає, Spring не потрібен.
 */
class BookMapperTest {

    private final BookMapper mapper = Mappers.getMapper(BookMapper.class);

    private static Book fullBook() {
        Book b = new Book();
        b.setId(1L);
        b.setTitle("Clean Code");
        b.setAuthor("Robert C. Martin");
        b.setIsbn("9780132350884");
        b.setPublishedYear(2008);
        b.setDescription("Handbook");
        return b;
    }

    private static BookDto fullDto() {
        BookDto d = new BookDto();
        d.setId(5L);
        d.setTitle("Effective Java");
        d.setAuthor("Joshua Bloch");
        d.setIsbn("9780134685991");
        d.setPublishedYear(2018);
        d.setDescription("Best practices");
        return d;
    }

    @Test
    void toDto_fullEntity_mapsAllFields() {
        // Arrange
        Book entity = fullBook();

        // Act
        BookDto dto = mapper.toDto(entity);

        // Assert
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Clean Code");
        assertThat(dto.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(dto.getIsbn()).isEqualTo("9780132350884");
        assertThat(dto.getPublishedYear()).isEqualTo(2008);
        assertThat(dto.getDescription()).isEqualTo("Handbook");
    }

    @Test
    void toDto_nullEntity_returnsNull() {
        // Arrange + Act
        BookDto dto = mapper.toDto(null);

        // Assert
        assertThat(dto).isNull();
    }

    @Test
    void toEntity_dtoWithId_ignoresIdAndMapsOtherFields() {
        // Arrange
        BookDto dto = fullDto();

        // Act
        Book entity = mapper.toEntity(dto);

        // Assert
        assertThat(entity.getId()).isNull();
        assertThat(entity.getTitle()).isEqualTo("Effective Java");
        assertThat(entity.getAuthor()).isEqualTo("Joshua Bloch");
        assertThat(entity.getIsbn()).isEqualTo("9780134685991");
        assertThat(entity.getPublishedYear()).isEqualTo(2018);
        assertThat(entity.getDescription()).isEqualTo("Best practices");
    }

    @Test
    void toEntity_nullDto_returnsNull() {
        // Arrange + Act
        Book entity = mapper.toEntity(null);

        // Assert
        assertThat(entity).isNull();
    }

    @Test
    void updateEntity_fullDto_overwritesFieldsButKeepsEntityId() {
        // Arrange
        Book target = fullBook();
        BookDto source = fullDto();

        // Act
        mapper.updateEntity(source, target);

        // Assert
        assertThat(target.getId()).isEqualTo(1L);
        assertThat(target.getTitle()).isEqualTo("Effective Java");
        assertThat(target.getAuthor()).isEqualTo("Joshua Bloch");
        assertThat(target.getPublishedYear()).isEqualTo(2018);
    }

    @Test
    void updateEntity_dtoWithNullOptionalField_setsFieldToNull() {
        // Arrange
        Book target = fullBook();
        BookDto source = fullDto();
        source.setDescription(null);

        // Act
        mapper.updateEntity(source, target);

        // Assert
        assertThat(target.getDescription()).isNull();
    }

    @Test
    void updateEntity_nullDto_leavesEntityUnchanged() {
        // Arrange
        Book target = fullBook();

        // Act
        mapper.updateEntity(null, target);

        // Assert
        assertThat(target.getTitle()).isEqualTo("Clean Code");
        assertThat(target.getId()).isEqualTo(1L);
    }

    @Test
    void toDtoList_severalEntities_mapsEachInOrder() {
        // Arrange
        Book second = fullBook();
        second.setId(2L);
        second.setTitle("Second");

        // Act
        List<BookDto> result = mapper.toDtoList(List.of(fullBook(), second));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Clean Code");
        assertThat(result.get(1).getTitle()).isEqualTo("Second");
    }

    @Test
    void toDtoList_emptyList_returnsEmptyList() {
        // Arrange + Act
        List<BookDto> result = mapper.toDtoList(List.of());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void toDtoList_nullList_returnsNull() {
        // Arrange + Act
        List<BookDto> result = mapper.toDtoList(null);

        // Assert
        assertThat(result).isNull();
    }
}
