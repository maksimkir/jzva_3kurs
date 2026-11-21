package com.example.library;

import org.springframework.boot.SpringApplication;
import com.example.library.async.AsyncProperties;
import com.example.library.scheduling.SchedulingProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({AsyncProperties.class, SchedulingProperties.class}) // ЛР5/ЛР6: library.async.*, library.scheduling.*
public class LibraryApplication {
    public static void main(String[] args) {
        // PostgreSQL 16 не знає старої назви пояса "Europe/Kiev", яку віддає Java на Windows
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LibraryApplication.class, args);
    }
}
