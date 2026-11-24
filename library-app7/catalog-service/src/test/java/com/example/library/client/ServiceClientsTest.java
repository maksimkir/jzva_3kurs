package com.example.library.client;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.common.notification.BookCreatedNotification;
import com.example.library.dto.BookDto;
import com.example.library.external.HttpBookInfoApi;
import com.example.library.notification.HttpNotificationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ЛР7 — HTTP-контракт catalog-service з іншими сервісами (без запуску тих сервісів): MockRestServiceServer
 * перевіряє URL, заголовок ключа, тіло запиту і розбір відповідей (DTO з модуля common).
 */
class ServiceClientsTest {

    private static BookDto book(String isbn) {
        BookDto dto = new BookDto();
        dto.setId(5L);
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        dto.setIsbn(isbn);
        return dto;
    }

    // ---------- book-info-service ----------
    private RestClient.Builder bookInfoBuilder() {
        return RestClient.builder().baseUrl("http://book-info");
    }

    @Test
    void rating_ok_parsesCommonDto() {
        RestClient.Builder builder = bookInfoBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://book-info/api/book-info/rating?isbn=9780132350884")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"available\":true,\"average\":4.4,\"votes\":120,\"error\":null}", MediaType.APPLICATION_JSON));

        RatingInfo rating = new HttpBookInfoApi(builder.build(), new ObjectMapper()).rating(book("9780132350884"));

        assertThat(rating).isEqualTo(RatingInfo.of(4.4, 120));
        server.verify();
    }

    @Test
    void rating_422_becomesExceptionWithServiceExplanation() {
        RestClient.Builder builder = bookInfoBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://book-info/api/book-info/rating"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":422,\"detail\":\"Сервіс рейтингів шукає книги лише за ISBN, а в книги його немає\"}"));

        assertThatThrownBy(() -> new HttpBookInfoApi(builder.build(), new ObjectMapper()).rating(book(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");
    }

    @Test
    void availability_ok_sendsTitleAndAuthor() {
        RestClient.Builder builder = bookInfoBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://book-info/api/book-info/availability?title=Clean%20Code&author=Robert%20C.%20Martin"))
                .andRespond(withSuccess("{\"available\":true,\"partnerLibraries\":2,\"copies\":5,\"error\":null}", MediaType.APPLICATION_JSON));

        AvailabilityInfo info = new HttpBookInfoApi(builder.build(), new ObjectMapper()).availability(book(null));

        assertThat(info).isEqualTo(AvailabilityInfo.of(2, 5));
    }

    // ---------- notification-service ----------
    @Test
    void bookCreated_postsCommonDtoWithInternalApiKey() {
        ServiceClientsProperties props = new ServiceClientsProperties("http://notification", "secret", "http://book-info", 1000, 1000);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://notification/internal/notifications/book-created")).andExpect(method(HttpMethod.POST))
                .andExpect(header(ServiceClientsConfig.API_KEY_HEADER, "secret"))
                .andExpect(content().json("{\"bookId\":5,\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\"}"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        RestClient client = builder.baseUrl(props.notificationUrl())
                .defaultHeader(ServiceClientsConfig.API_KEY_HEADER, props.notificationApiKey()).build();
        new HttpNotificationClient(client).bookCreated(new BookCreatedNotification(5L, "Clean Code", "Robert C. Martin"));

        server.verify();
    }

    @Test
    void bookCreated_serviceError_throwsSoAsyncHandlerRecordsIt() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://notification");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://notification/internal/notifications/book-created"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> new HttpNotificationClient(builder.build())
                .bookCreated(new BookCreatedNotification(5L, "Clean Code", "Robert C. Martin")))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void config_buildsClientsWithBaseUrlsAndTimeouts() {
        ServiceClientsConfig config = new ServiceClientsConfig();
        ServiceClientsProperties props = new ServiceClientsProperties("http://n", "k", "http://b", 500, 700);

        assertThat(config.notificationRestClient(RestClient.builder(), props)).isNotNull();
        assertThat(config.bookInfoRestClient(RestClient.builder(), props)).isNotNull();
    }
}
