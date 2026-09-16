package com.example.laptopshop.dto.response;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Thông tin 1 thiết bị đang đăng nhập, trả cho FE hiển thị danh sách. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceInfoResponse {

    String deviceId;
    String deviceName; // "Chrome - Windows"
    String ipAddress;
    LocalDateTime createdAt; // đăng nhập lần đầu
    LocalDateTime lastActiveAt; // hoạt động gần nhất

    // Thiết bị đang gửi request này (FE đánh dấu "Thiết bị này" / chặn tự đá).
    boolean current;
}
