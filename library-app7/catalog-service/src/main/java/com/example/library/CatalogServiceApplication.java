package com.example.library;

import org.springframework.boot.SpringApplication;
import com.example.library.async.AsyncProperties;
import com.example.library.client.ServiceClientsProperties;
import com.example.library.scheduling.SchedulingProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

/** ЛР7: власний main-клас catalog-service (порт 8080). */
@SpringBootApplication
@EnableConfigurationProperties({AsyncProperties.class, SchedulingProperties.class, ServiceClientsProperties.class})
public class CatalogServiceApplication {
    public static void main(String[] args) {
        // PostgreSQL 16 не знає старої назви пояса "Europe/Kiev", яку віддає Java на Windows
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
