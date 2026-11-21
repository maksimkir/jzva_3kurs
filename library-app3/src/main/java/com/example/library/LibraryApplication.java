package com.example.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class LibraryApplication {
    public static void main(String[] args) {
        // PostgreSQL 16 не знає старої назви пояса "Europe/Kiev", яку віддає Java на Windows
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LibraryApplication.class, args);
    }
}
