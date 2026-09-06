package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Token dùng cho luồng "Quên mật khẩu" của khách hàng (storefront).
 *
 * <p>Luồng sử dụng:
 * <ol>
 *   <li>User gửi email -> sinh token ngẫu nhiên, lưu DB kèm expiry (1 giờ).</li>
 *   <li>Gửi link chứa token qua email cho user.</li>
 *   <li>User bấm link -> gửi token + mật khẩu mới -> server check hạn + đã dùng,
 *       set mật khẩu mới, đánh dấu token đã dùng.</li>
 * </ol>
 *
 * <p>Token là chuỗi ngẫu nhiên (UUID) — không phải JWT. Lưu DB để có thể thu hồi
 * (set used=true) ngay khi dùng xong, tránh bị replay.
 */
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiry;

    private boolean used = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
