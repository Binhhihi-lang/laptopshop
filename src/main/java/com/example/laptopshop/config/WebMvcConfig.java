package com.example.laptopshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        // tắt security csrf
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .anyRequest().permitAll());

                return http.build();
        }

        // Lấy giá trị từ application.properties
        // 1. Lấy giá trị đường dẫn vật lý (D:/Spring/laptopshop/uploads/) từ file
        // application.properties
        @Value("${upload.directory}")
        private String uploadDirectory;

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // "file:///" là bắt buộc cho Windows để chỉ đường dẫn vật lý trên ổ cứng
                registry.addResourceHandler("/images-upload/**")
                                .addResourceLocations("file:///" + uploadDirectory);
        }
}
