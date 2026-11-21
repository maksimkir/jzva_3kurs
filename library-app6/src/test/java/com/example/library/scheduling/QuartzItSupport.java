package com.example.library.scheduling;

import com.example.library.digest.NewBooksDigestService;
import com.example.library.entity.Book;
import com.example.library.notification.MailGateway;
import com.example.library.report.CatalogReportService;
import com.example.library.repository.BookRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Мінімальний Spring Boot застосунок для integration-тестів ЛР6: справжні автоконфігурації DataSource, Liquibase,
 * JPA і Quartz (JDBC job store) на H2. Без web/security/Keycloak — їх тут не потрібно.
 * Підтягує той самий application.yml (spring.quartz.*, library.scheduling.*), що й робочий застосунок.
 */
final class QuartzItSupport {

    private QuartzItSupport() {
    }

    /** Властивості, що відрізняються від робочого PostgreSQL. */
    static final String[] H2_PROPERTIES = {
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.datasource.driver-class-name=org.h2.Driver",
            // PostgreSQLDelegate працює з bytea; для H2 — стандартний делегат
            "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate",
            "spring.quartz.properties.org.quartz.jobStore.isClustered=false",
    };

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(SchedulingProperties.class)
    @EntityScan(basePackageClasses = Book.class)
    @EnableJpaRepositories(basePackageClasses = BookRepository.class)
    @Import({QuartzJobsConfig.class, NewBooksDigestService.class, CatalogReportService.class})
    static class App {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        RecordingMailGateway mailGateway() {
            return new RecordingMailGateway();
        }
    }

    /** «SMTP», що запам'ятовує листи і може «впасти» на вимогу тесту. */
    static class RecordingMailGateway implements MailGateway {
        final List<String> subjects = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @Override
        public void send(String to, String subject, String body) {
            if (fail) {
                throw new IllegalStateException("SMTP down (test)");
            }
            subjects.add(subject);
        }
    }
}
