package com.example.laptopshop.dto.response;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Payload trả kèm lỗi 1013 (vượt giới hạn thiết bị): danh sách thiết bị đang
 * đăng nhập + vé tạm để FE gọi "đăng xuất thiết bị cũ nhất" mà không phải gửi
 * lại mật khẩu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceLimitResponse {

    List<DeviceInfoResponse> devices;

    // Vé dùng 1 lần, TTL ngắn. Null ở các ngữ cảnh không cần (vd liệt kê thiết bị).
    String revokeTicket;

    int maxSessions;
}
