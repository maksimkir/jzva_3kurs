package com.example.library.repository;

import com.example.library.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    /** ЛР6: для звіту по каталогу. */
    long countByIsbnIsNotNull();

    /** ЛР6: [author, кількість книг] у порядку спадання кількості. */
    @Query("select b.author, count(b) from Book b group by b.author order by count(b) desc, b.author asc")
    List<Object[]> findTopAuthors(Pageable pageable);
}
