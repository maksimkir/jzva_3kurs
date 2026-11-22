package com.example.library.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** ЛР7 — HTTP-клієнти до інших сервісів з обмеженими тайм-аутами (повільний сервіс не підвісить потік назавжди). */
@Configuration
public class ServiceClientsConfig {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    RestClient notificationRestClient(RestClient.Builder builder, ServiceClientsProperties props) {
        return builder.clone()
                .baseUrl(props.notificationUrl())
                .defaultHeader(API_KEY_HEADER, props.notificationApiKey())
                .requestFactory(requestFactory(props))
                .build();
    }

    @Bean
    RestClient bookInfoRestClient(RestClient.Builder builder, ServiceClientsProperties props) {
        return builder.clone()
                .baseUrl(props.bookInfoUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(ServiceClientsProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return factory;
    }
}
