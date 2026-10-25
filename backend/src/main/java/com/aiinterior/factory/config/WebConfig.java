package com.aiinterior.factory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FactoryProperties props;

    public WebConfig(FactoryProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
