package com.example.laptopshop.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;


import com.example.laptopshop.domain.InvalidatedToken;
import com.example.laptopshop.domain.RefreshToken;
import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.repository.InvalidatedTokenRepository;
import com.example.laptopshop.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Auth.AuthenticationRequest;
import com.example.laptopshop.dto.request.Auth.IntrospectRequest;
import com.example.laptopshop.dto.response.AuthenticationResponse;
import com.example.laptopshop.dto.response.IntrospectResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;


    // Khóa bí mật để ký/verify JWT (thuật toán đối xứng HS512) -> đọc từ
    // application.properties, KHÔNG hardcode trong code, KHÔNG commit key thật lên
    // git.
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Thời hạn token, đơn vị giây
    @Value("${jwt.valid-duration}")
    private long validDuration;

    // Thời hạn refresh token
    @Value("${jwt.refreshable-duration}")
    protected long refreshableDuration;


    public AuthenticationService(UserService userService, PasswordEncoder passwordEncoder, InvalidatedTokenRepository invalidatedTokenRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ================== AUTHENTICATE ==================

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        User user = this.userService.getUserByEmail(request.getEmail().trim().toLowerCase());
        if (user == null) {
            log.warn("Dang nhap that bai: khong tim thay user voi email={}", request.getEmail());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean authenticated = this.passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            log.warn("Dang nhap that bai: sai mat khau. userId={}", user.getId());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Kiểm tra tài khoản có bị khóa (active = false) không
        if (!user.isActive()) {
            log.warn("Dang nhap that bai: tai khoan da bi khoa. userId={}", user.getId());
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // Cập nhật lastLoginAt sau khi xác thực thành công
        this.userService.updateLastLoginAt(user.getId(), LocalDateTime.now());

        try {
            return issueTokenPair(user);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // ================== INTROSPECT ==================

    public IntrospectResponse introspect(IntrospectRequest request) {
        boolean valid = true;
        try {
            verifyToken(request.getToken());
        } catch (AppException | JOSEException | ParseException e) {
            valid = false;
        }
        IntrospectResponse response = new IntrospectResponse();
        response.setValid(valid);
        return response;
    }

    // ================== LOGOUT ==================

    public void logout(LogoutRequest request) {
        // 1. Blacklist access token (nếu còn verify được)
        try {
            SignedJWT signedJWT = verifyToken(request.getToken());
            String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            long ttl = (expiryTime.getTime() - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                this.invalidatedTokenRepository.save(new InvalidatedToken(jwtId, ttl));
            }
        } catch (AppException | JOSEException | ParseException e) {
            log.warn("Logout: access token khong hop le/da het han, bo qua buoc blacklist.");
        }

        // 2. Thu hồi refresh token (xóa khỏi whitelist Redis)
        if (request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                String refreshJwtId = SignedJWT.parse(request.getRefreshToken()).getJWTClaimsSet().getJWTID();
                this.refreshTokenRepository.deleteById(refreshJwtId);
            } catch (ParseException e) {
                log.warn("Logout: refresh token khong hop le, bo qua buoc thu hoi.");
            }
        }
    }

    // ================== REFRESH TOKEN ==================

    @Transactional(readOnly = true)
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        try {
            SignedJWT signedJWT = verifyRefreshToken(request.getRefreshToken());
            String oldJwtId = signedJWT.getJWTClaimsSet().getJWTID();
            String userId = signedJWT.getJWTClaimsSet().getStringClaim("userId");

            // Giữ nguyên hạn tuyệt đối của refresh token gốc -> phiên đăng nhập
            // có thời gian cố định, không bị refresh liên tục để kéo dài vô hạn
            Date absoluteExpiry = signedJWT.getJWTClaimsSet().getExpirationTime();

            // Xoay vòng: refresh token cũ dùng 1 lần rồi xóa ngay, chống replay
            this.refreshTokenRepository.deleteById(oldJwtId);

            User user = this.userService.getUserById(userId);

            String newAccessToken = generateToken(user, false);
            String newRefreshToken = generateRefreshTokenWithExpiry(user, absoluteExpiry);
            saveRefreshToken(user.getId(), newRefreshToken);

            AuthenticationResponse response = new AuthenticationResponse();
            response.setToken(newAccessToken);
            response.setRefreshToken(newRefreshToken);
            response.setAuthenticated(true);
            return response;
        } catch (JOSEException | ParseException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    // Thu hồi TOÀN BỘ refresh token của 1 user -> đá mọi thiết bị khác về
    // login lại. Gọi ngay sau khi đổi mật khẩu thành công (khi bạn làm tính năng đó).
    public void revokeAllRefreshTokens(String userId) {
        var tokens = this.refreshTokenRepository.findByUserId(userId);
        this.refreshTokenRepository.deleteAll(tokens);
        log.info("Da thu hoi {} refresh token cua userId={}", tokens.size(), userId);
    }

    // ================== TẠO TOKEN ==================

    // Dùng chung cho access token (isRefresh=false) và refresh token (isRefresh=true)
    private String generateToken(User user, boolean isRefresh) {
        Date now = new Date();
        long duration = isRefresh ? refreshableDuration : validDuration;
        Date expirationTime = new Date(now.getTime() + duration * 1000);
        return buildAndSignToken(user, now, expirationTime);
    }

    // Dùng lúc refresh: refresh token mới nhưng GIỮ NGUYÊN hạn hết hiệu lực
    private String generateRefreshTokenWithExpiry(User user, Date expirationTime) {
        return buildAndSignToken(user, new Date(), expirationTime);
    }

    private String buildAndSignToken(User user, Date issueTime, Date expirationTime) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getFullName())
                .issuer("laptopshop.com")
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getId())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    // Tạo cả cặp accessToken + refreshToken, lưu refreshToken vào Redis (whitelist)
    private AuthenticationResponse issueTokenPair(User user) throws ParseException {
        String accessToken = generateToken(user, false);
        String refreshToken = generateToken(user, true);
        saveRefreshToken(user.getId(), refreshToken);

        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setAuthenticated(true);
        return response;
    }

    private void saveRefreshToken(String userId, String refreshToken) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(refreshToken);
        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        long ttl = (expiryTime.getTime() - System.currentTimeMillis()) / 1000;

        this.refreshTokenRepository.save(new RefreshToken(jwtId, userId, ttl));
    }

    // ================== VERIFY TOKEN ==================

    // Access token: hợp lệ khi chữ ký đúng + còn hạn + KHÔNG nằm trong blacklist Redis
    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        if (this.invalidatedTokenRepository.existsById(jwtId)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    // Refresh token: hợp lệ khi chữ ký đúng + còn hạn + TỒN TẠI
    // Redis. Tách riêng lỗi "hết hạn" / "không tồn tại" để FE phân biệt được.
    private SignedJWT verifyRefreshToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        if (!signedJWT.verify(verifier)) {
            log.warn("Refresh token sai chu ky.");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (!signedJWT.getJWTClaimsSet().getExpirationTime().after(new Date())) {
            log.warn("Refresh token da het han. jwtId={}", signedJWT.getJWTClaimsSet().getJWTID());
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        if (!this.refreshTokenRepository.existsById(jwtId)) {
            // CHỈ log jwtId
            log.warn("Refresh token khong ton tai trong Redis (da dung / da thu hoi / khong hop le). jwtId={}",
                    jwtId);
            throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return signedJWT;
    }


    // Claim "scope" chứa các tên Role cách nhau bởi khoảng trắng (vd "ADMIN
    // USER") -> JwtGrantedAuthoritiesConverter bên SecurityConfiguration TỰ
    // ĐỘNG split chuỗi theo khoảng trắng thành nhiều quyền "ROLE_ADMIN",
    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            user.getRoles().forEach(role -> {
                // 1. Thêm Role với tiền tố ROLE_
                stringJoiner.add("ROLE_" + role.getName());

                // 2. Thêm tất cả Permission thuộc Role này (KHÔNG có tiền tố ROLE_)
                if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                    role.getPermissions().forEach(permission -> {
                        stringJoiner.add(permission.getName());
                    });
                }
            });
        }

        return stringJoiner.toString();
    }

}