package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.text.ParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.RefreshToken;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.InvalidatedTokenRepository;
import com.example.laptopshop.repository.RefreshTokenRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Unit test cho AuthenticationService (JUnit thuần + Mockito, KHÔNG boot Spring).
 * Hiện tại tập trung vào luồng refreshToken — PA2: user bị KHÓA không được
 * cấp lại access token, phiên phải kết thúc ngay ở lần refresh đầu tiên.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthenticationService authenticationService;

    // signerKey HS512 yêu cầu >= 64 bytes, chỉ dùng trong test
    private static final String TEST_SIGNER_KEY =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private final String SAMPLE_USER_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    @BeforeEach
    void setUp() throws Exception {
        authenticationService = new AuthenticationService(
                userService, null, invalidatedTokenRepository, refreshTokenRepository);
        // @Value không chạy ngoài Spring -> set trực tiếp bằng reflection
        var signerKeyField = AuthenticationService.class.getDeclaredField("signerKey");
        signerKeyField.setAccessible(true);
        signerKeyField.set(authenticationService, TEST_SIGNER_KEY);
        var validDurationField = AuthenticationService.class.getDeclaredField("validDuration");
        validDurationField.setAccessible(true);
        validDurationField.setLong(authenticationService, 21600L);
        var refreshableField = AuthenticationService.class.getDeclaredField("refreshableDuration");
        refreshableField.setAccessible(true);
        refreshableField.setLong(authenticationService, 864000L);
    }

    // Tạo 1 refresh token hợp lệ (ký thật) với hạn tuyệt đối trong tương lai,
    // claim userId trỏ tới SAMPLE_USER_ID — mô phỏng đúng token BE cấp khi login.
    private String buildValidRefreshToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("John Doe")
                .jwtID("refresh-jwt-id-1")
                .claim("userId", SAMPLE_USER_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 864000_000L))
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS512), claims);
        signedJWT.sign(new MACSigner(TEST_SIGNER_KEY.getBytes()));
        return signedJWT.serialize();
    }

    @Test
    void refreshToken_userInactive_throwUserInactive() throws Exception {
        // GIVEN: refresh token hợp lệ còn hạn, nhưng user trong DB đã bị KHÓA
        String token = buildValidRefreshToken();
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);

        User lockedUser = new User();
        lockedUser.setId(SAMPLE_USER_ID);
        lockedUser.setEmail("locked@example.com");
        lockedUser.setActive(false); // <- bị khóa sau khi login

        // Refresh token còn tồn tại trong whitelist Redis (chưa bị thu hồi)
        when(refreshTokenRepository.existsById("refresh-jwt-id-1")).thenReturn(true);
        when(userService.getUserById(SAMPLE_USER_ID)).thenReturn(lockedUser);

        // WHEN & THEN: phải ném USER_INACTIVE thay vì cấp cặp token mới ->
        // FE nhận lỗi từ /auth/refresh -> interceptor logout ngay lập tức.
        AppException exception = assertThrows(AppException.class,
                () -> authenticationService.refreshToken(request));
        assertEquals(ErrorCode.USER_INACTIVE, exception.getErrorCode());

        // Không được cấp phát gì mới: refresh token cũ đã bị xóa (xoay vòng)
        // nhưng KHÔNG lưu refresh token mới cho user bị khóa.
        verify(refreshTokenRepository).deleteById("refresh-jwt-id-1");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
