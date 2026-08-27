package com.example.laptopshop.config;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(@NonNull HttpServletRequest request, HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) throws IOException {
        // Chạy TRONG filter chain (ExceptionTranslationFilter) khi URL matcher
        // "/api/v1/admin/**" chặn -> @RestControllerAdvice KHÔNG bắt được, phải tự
        // ghi JSON ra response như JwtAuthenticationEntryPoint.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allAuthoritiesRevoked = authentication != null
                && authentication.isAuthenticated()
                && (authentication.getAuthorities().isEmpty());

        // Token hợp lệ nhưng authorities RỖNG (khóa role / xóa mềm user) -> trả code
        // 6012 để FE logout ngay. Còn lại (vẫn có quyền nhưng không đủ cho endpoint)
        // -> trả 1006, giống hệt @PreAuthorize bị từ chối.
        ErrorCode errorCode = allAuthoritiesRevoked ? ErrorCode.ACCESS_REVOKED : ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }
}
