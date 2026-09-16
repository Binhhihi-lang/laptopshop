package com.example.laptopshop.domain;

import java.io.Serializable;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "REFRESH_TOKEN")
public class RefreshToken implements Serializable {

    @Id
    private String id; // jwtId của refresh token

    @Indexed // đánh index để query được theo userId -> phục vụ revoke-all-device
    private String userId;

    // Thiết bị sở hữu refresh token này. Nhờ @Indexed -> thu hồi được MỌI
    // refresh token của 1 thiết bị khi user bấm "đăng xuất thiết bị".
    // Null với token cũ cấp trước khi có tính năng này (backward compatible).
    @Indexed
    private String deviceId;

    @TimeToLive
    private Long ttl;
}