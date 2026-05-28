package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${mall.admin.base-url:http://localhost:8080}")
    private String mallAdminBaseUrl;

    @Bean
    public RestClient mallAdminRestClient() {
        return RestClient.builder()
                .baseUrl(mallAdminBaseUrl)
                .build();
    }
}
