package com.example.laptopshop.config;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    // Khóa bí mật ký/verify JWT, phải trùng với key AuthenticationService dùng để
    // ký token
    @Value("${jwt.signerKey}")
    private String signerKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. Cấu hình phân quyền API
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // mặc định bật cấu hình csrf : là bảo vệ endpoint attack CROT
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF vì làm API (Stateless)
                // Không dùng session của server nữa, mọi request tự chứng minh danh tính bằng
                // JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 1. Cho phép tải toàn bộ tài nguyên tĩnh phục vụ giao diện (BẬT LÊN)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico").permitAll()
                        // Cấu hình các API Public (Ai cũng có thể truy cập mà không cần Token)
                        .requestMatchers("/api/v1/auth/**").permitAll() // API đăng nhập, introspect
                        .requestMatchers("/admin/**").permitAll() // trang login
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll() // Khách xem sản phẩm
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll() // Khách xem danh mục

                        // Toàn bộ khu vực quản trị chỉ ADMIN mới được vào. Sửa lại đúng theo path THẬT
                        // đang tồn tại trong dự án (trước đây rule "/api/v1/admin/**" không khớp
                        // controller nào cả nên hoàn toàn không có tác dụng).
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/coupons/**").hasRole("ADMIN") // Chỉ ADMIN mới quản lý coupon
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasRole("ADMIN")

                        // Tất cả các request khác đều bắt buộc phải đăng nhập (Có token hợp lệ)
                        .anyRequest().authenticated())
                // 3. Bật OAuth2 Resource Server để Spring tự verify JWT trên mỗi request có
                // header Authorization: Bearer <token>, dùng jwtDecoder() +
                // jwtAuthenticationConverter()
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                // Token thiếu/sai/hết hạn -> trả JSON đúng format ApiResponse thay vì
                // whitelabel error mặc định của Spring
                );

        return http.build();
    }

    // 4. Bean giải mã & verify chữ ký JWT (đối xứng, thuật toán HS512, cùng key
    // với lúc AuthenticationService ký token)
    @Bean
    public JwtDecoder jwtDecoder() {
        // lấy khóa bí mật
        SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HS512");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    // 5. Map claim "scope" trong token (vd giá trị "ADMIN") thành quyền Spring
    // Security "ROLE_ADMIN" -> để .hasRole("ADMIN") ở trên hoạt động đúng
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

}