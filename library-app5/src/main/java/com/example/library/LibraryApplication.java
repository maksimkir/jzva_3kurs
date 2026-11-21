package com.example.library;

import org.springframework.boot.SpringApplication;
import com.example.library.async.AsyncProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AsyncProperties.class) // ЛР5: library.async.* з application.yml
public class LibraryApplication {
    public static void main(String[] args) {
        // PostgreSQL 16 не знає старої назви пояса "Europe/Kiev", яку віддає Java на Windows
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LibraryApplication.class, args);
    }
}
