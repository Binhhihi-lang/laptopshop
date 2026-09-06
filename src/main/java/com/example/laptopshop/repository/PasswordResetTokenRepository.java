package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    // Dọn token đã hết hạn (chạy định kỳ bằng @Scheduled hoặc gọi tay khi cần).
    // Trả về số bản ghi bị xoá.
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiry < :now")
    int deleteByExpiryBefore(@Param("now") LocalDateTime now);
}
