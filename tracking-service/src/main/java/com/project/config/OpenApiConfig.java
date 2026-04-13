package com.project.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trackingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tracking Service API")
                        .description("Handles real-time rider location ingestion and rider pool management")
                        .version("1.0.0")
                        .contact(new Contact().name("Logistics System")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")));
    }
}
