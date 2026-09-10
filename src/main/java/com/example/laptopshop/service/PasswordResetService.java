package com.example.laptopshop.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.PasswordResetToken;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.PasswordResetTokenRepository;
import com.example.laptopshop.repository.RefreshTokenRepository;
import com.example.laptopshop.repository.UserRepository;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Xử lý luồng "Quên mật khẩu" cho nhánh client (storefront).
 *
 * <p>Luồng:
 * <ol>
 *   <li>{@link #requestReset(String)}: nhận email -> sinh token ngẫu nhiên +
 *       lưu DB + gửi mail link reset.</li>
 *   <li>{@link #resetPassword(String, String)}: nhận token + mật khẩu mới ->
 *       check hạn + đã dùng, đổi mật khẩu, đánh dấu token đã dùng, thu hồi
 *       toàn bộ refresh token của user (force đăng nhập lại).</li>
 * </ol>
 *
 * <p>Lưu ý: KHÔNG tiết lộ "email không tồn tại" trong response (tránh dò email).
 * Luôn trả về thành công ở {@link #requestReset(String)} dù email có/không có
 * trong DB.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Hash Reset token để lưu và so sánh
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    final UserRepository userRepository;
    final PasswordResetTokenRepository tokenRepository;
    final RefreshTokenRepository refreshTokenRepository;
    final PasswordEncoder passwordEncoder;
    final EmailService emailService;

    // Cấu hình inject qua constructor (KHÔNG dùng field @Value) vì
    // @FieldDefaults(makeFinal=true) của Lombok làm field final -> Spring không
    // thể inject sau khi constructor chạy.
    final int tokenValidityMinutes;
    final String resetBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.reset-password.token-validity-minutes:60}") int tokenValidityMinutes,
            @Value("${app.reset-password.base-url:http://localhost:4200/client/reset-password}") String resetBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tokenValidityMinutes = tokenValidityMinutes;
        this.resetBaseUrl = resetBaseUrl;
    }

    /**
     * Bước 1: user nhập email -> gửi link reset qua email.
     * Trả về luôn "thành công" dù email có tồn tại hay không (tránh email enumeration).
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isEmpty()) {
            // Không throw để tránh leak logic; chỉ return sớm.
            return;
        }
        User user = this.userRepository.findByEmail(normalized);
        if (user == null || !user.isActive()) {
            // Không tiết lộ user không tồn tại / bị khóa -> chỉ return.
            return;
        }

        // Sinh token ngẫu nhiên 32 byte, encode base64url (~43 ký tự).
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(sha256Hex(token));
        entity.setExpiry(LocalDateTime.now().plusMinutes(this.tokenValidityMinutes));
        entity.setUsed(false);
        this.tokenRepository.save(entity);

        String link = this.resetBaseUrl + "?token=" + token;
        this.emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), link);
    }

    /**
     * Bước 2: user bấm link, nhập mật khẩu mới.
     * Token không hợp lệ / hết hạn / đã dùng -> throw AppException.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        // check token của được sử dụng chưa hay đã hết hạn
        PasswordResetToken entity = this.tokenRepository.findByTokenHash(sha256Hex(token))
                .orElseThrow(() -> new AppException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        if (entity.isUsed()) {
            throw new AppException(ErrorCode.PASSWORD_RESET_TOKEN_USED);
        }
        if (entity.getExpiry().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        User user = entity.getUser();
        user.setPassword(this.passwordEncoder.encode(newPassword));
        this.userRepository.save(user);

        // Đánh dấu token đã dùng để tránh replay.
        entity.setUsed(true);
        this.tokenRepository.save(entity);

        // Thu hồi toàn bộ refresh token hiện có -> ép user đăng nhập lại bằng
        // mật khẩu mới. Tương tự change-password.
        // Dùng repository trực tiếp (không inject AuthenticationService) để tránh
        // circular dependency.
        var tokens = this.refreshTokenRepository.findByUserId(user.getId());
        this.refreshTokenRepository.deleteAll(tokens);
    }
}
