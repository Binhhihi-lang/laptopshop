package com.example.laptopshop.config;

import java.util.Collections;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.example.laptopshop.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Converter tùy biến thay thế JwtGrantedAuthoritiesConverter mặc định.
 *
 * <p>Thay vì tin vào claim "scope" nằm sẵn trong token (có thể đã lỗi thời),
 * converter này đọc claim "userId" rồi load {@link com.example.laptopshop.domain.User}
 * từ DB để xây lại danh sách quyền (authorities) từ CHỈ CÁC ROLE ĐANG ACTIVE.
 * Khi một Role bị khóa (active=false) hoặc xóa mềm, quyền tương ứng sẽ bị
 * thu hồi ngay trên request tiếp theo (không cần logout / cấp lại token).
 */
@RequiredArgsConstructor
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = Collections.emptyList();
        String userId = jwt.getClaimAsString("userId");
        if (userId != null && !userId.isBlank()) {
            // lấy người dùng có active = true
            authorities = this.userService.getActiveAuthorities(userId);
        }
        // Giữ nguyên principal = subject (fullName) như converter mặc định để
        // không phá các chỗ đọc authentication.getName() hiện tại.
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("sub"));
    }
}
