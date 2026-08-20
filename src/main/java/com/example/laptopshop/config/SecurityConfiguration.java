package com.example.laptopshop.config;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.example.laptopshop.service.UserService;

@AllArgsConstructor
@Configuration
@EnableMethodSecurity(securedEnabled = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfiguration {

    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    CustomJwtDecoder customJwtDecoder;
    CorsConfig config;
    UserService userService;

    // 2. Cấu hình phân quyền API
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) {
        httpSecurity
                // 1. Mở CORS để nhận request từ Frontend http://localhost:3000
//                .cors(cors -> cors.configurationSource(config.corsConfigurationSource()))
                // mặc định bật cấu hình csrf : là bảo vệ endpoint attack CROT
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF vì làm API (Stateless)
                // Không dùng session của server nữa, mọi request tự chứng minh danh tính bằng
                // JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Các tài nguyên giao diện của admin
                        .requestMatchers("/api/v1/admin/auth/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Toàn bộ khu vực quản trị: ADMIN và STAFF được vào
                        // (phòng thủ thô - defense-in-depth). Kiểm soát chi tiết
                        // chuyển sang @PreAuthorize từng endpoint (Phase D).
                        // CUSTOMER bị chặn hoàn toàn ở tầng path này.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "STAFF")

                        // Của khách hàng
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/client/**").permitAll()

                        // Tất cả các request khác đều bắt buộc phải đăng nhập (Có token hợp lệ)
                        .anyRequest().authenticated())
                // 3. Bật OAuth2 Resource Server để Spring tự verify JWT trên mỗi request có
                // header Authorization: Bearer <token>, dùng customJwtDecoder() giải mã +
                // jwtAuthenticationConverter()
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt ->
                                jwt.decoder(customJwtDecoder) // giải mã token check redis
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // bắt lỗi 401, Token thiếu/sai/hết hạn
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint));

        return httpSecurity.build();
    }
    // 5. Converter tùy biến: load User từ DB (theo claim "userId") và build
    // authorities từ các Role ĐANG ACTIVE. Thay thế converter mặc định vốn chỉ
    // tin vào claim "scope" tĩnh trong token. Nhờ vậy, khóa một Role sẽ thu hồi
    // quyền trên request tiếp theo (không cần logout).
    @Bean
    public CustomJwtAuthenticationConverter jwtAuthenticationConverter() {
        return new CustomJwtAuthenticationConverter(this.userService);
    }



}