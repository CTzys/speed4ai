package com.speednet.custom.config;

import com.speednet.custom.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    @Value("${custom.cors-origin}") private String corsOrigin;

    public WebConfig(AuthInterceptor authInterceptor) { this.authInterceptor = authInterceptor; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/custom-api/**")
                .excludePathPatterns("/custom-api/auth/email-code", "/custom-api/auth/register", "/custom-api/auth/login", "/custom-api/auth/refresh");
    }

    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/custom-api/**").allowedOrigins(corsOrigin).allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
