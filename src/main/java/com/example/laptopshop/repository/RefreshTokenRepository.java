package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.example.laptopshop.domain.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    // Nhờ @Indexed ở userId -> dùng để thu hồi TOÀN BỘ refresh token của 1
    // user (đăng xuất mọi thiết bị khi đổi mật khẩu / nghi ngờ bị lộ tài khoản)
    List<RefreshToken> findByUserId(String userId);
}