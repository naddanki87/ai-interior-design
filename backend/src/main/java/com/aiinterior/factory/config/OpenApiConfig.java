package com.aiinterior.factory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI factoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI Interior Design Factory API")
                .version("0.1.0")
                .description("Start a design session, watch the agents collaborate, approve, and export to SketchUp."));
    }
}
