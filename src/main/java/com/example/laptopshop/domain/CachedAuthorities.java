package com.example.laptopshop.domain;

import java.io.Serializable;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

// Cache danh sách quyền (authorities) của 1 user vào Redis, key = userId.
// Thay vì query DB trên mỗi request, CustomJwtAuthenticationConverter đọc cache
// này trước (0 query DB khi hit). TTL ngắn (300s) làm "mạng lưới an toàn" phòng
// khi quên evict, còn thu hồi NGAY được thực hiện bằng cách xóa key khi Role/
// User thay đổi (xem UserService.evictUserAuthorities / evictUsersOfRoles).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@RedisHash(value = "USER_AUTHORITIES")
public class CachedAuthorities implements Serializable {

    @Id
    String userId; // = userId của User

    List<String> authorities; // ["ROLE_ADMIN", "PRODUCT_VIEW", ...]

    @TimeToLive // đơn vị giây, Redis tự xóa key khi hết hạn
    Long ttl;
}
