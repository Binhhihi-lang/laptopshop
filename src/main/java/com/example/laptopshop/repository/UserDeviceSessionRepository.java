package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.example.laptopshop.domain.UserDeviceSession;

// Redis Repository (CrudRepository, KHÔNG phải JpaRepository) — giống
// RefreshTokenRepository / CachedAuthoritiesRepository.
public interface UserDeviceSessionRepository extends CrudRepository<UserDeviceSession, String> {

    // Nhờ @Indexed ở userId -> đếm & liệt kê thiết bị đang đăng nhập của 1 user.
    List<UserDeviceSession> findByUserId(String userId);
}
