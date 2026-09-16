package com.example.laptopshop.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Phiên đăng nhập của 1 THIẾT BỊ (không phải 1 lần login).
 *
 * <p>Key = {@code userId:deviceId} nên login lại trên cùng thiết bị sẽ GHI ĐÈ
 * phiên cũ thay vì tạo thêm -> 1 thiết bị luôn giữ đúng 1 phiên. Nhờ vậy đếm
 * số bản ghi theo userId chính là đếm số thiết bị đang đăng nhập.
 *
 * <p>{@code deviceId} do FE sinh (UUID, localStorage) và gửi qua header
 * {@code X-Device-Id}. {@code deviceName} do BE parse từ User-Agent.
 *
 * <p>TTL bằng thời hạn refresh token -> phiên tự hết hạn nếu user bỏ app.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@RedisHash(value = "USER_DEVICE_SESSION")
public class UserDeviceSession implements Serializable {

    @Id
    String id; // userId + ":" + deviceId

    @Indexed // query theo user -> đếm/liệt kê thiết bị đang đăng nhập
    String userId;

    @Indexed // query theo thiết bị -> thu hồi mọi phiên của 1 máy
    String deviceId;

    String deviceName; // "Chrome - Windows" (BE parse từ User-Agent, whitelist)

    String ipAddress;

    LocalDateTime createdAt;

    LocalDateTime lastActiveAt;

    @TimeToLive // đơn vị giây, Redis tự xóa key khi hết hạn
    Long ttl;

    /** Khóa Redis của phiên: 1 user + 1 thiết bị = 1 phiên duy nhất. */
    public static String buildId(String userId, String deviceId) {
        return userId + ":" + deviceId;
    }
}
