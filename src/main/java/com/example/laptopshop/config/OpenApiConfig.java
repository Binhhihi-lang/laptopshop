package com.example.laptopshop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * BEAN 1: Cấu hình OpenAPI tổng quan + Cơ chế xác thực JWT Token
     * Giúp hiển thị nút "Authorize" ở góc phải giao diện Swagger UI.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LaptopShop RESTful API")
                        .version("1.0.0")
                        .description("Tài liệu tích hợp Swagger OpenAPI cho hệ thống Bán Laptop (Spring Boot + Angular)")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                // Yêu cầu xác thực Security mặc định cho toàn bộ API
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // Định nghĩa Security Scheme kiểu HTTP Bearer JWT
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Dán chuỗi JWT Token thu được từ API Login vào đây (KHÔNG cần gõ từ 'Bearer ')")));
    }

    /**
     * BEAN 2: Cấu hình Nhóm API (GroupedOpenApi)
     * Lọc và gom nhóm các API bảo mật dành cho Admin / Yêu cầu Token
     */
    @Bean
    public GroupedOpenApi adminApiGroup() {
        return GroupedOpenApi.builder()
                .group("1-admin-management") // Tên group hiển thị ở Menu dropdown trên Swagger
                .pathsToMatch("/api/v1/admin/**", "/api/v1/users/**", "/api/v1/products/**") // Các path API thuộc nhóm này
                .build();
    }
}