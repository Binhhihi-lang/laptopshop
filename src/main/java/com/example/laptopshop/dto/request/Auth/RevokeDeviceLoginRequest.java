package com.example.laptopshop.dto.request.Auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Yêu cầu "đăng xuất thiết bị đã chọn" gửi kèm lỗi 1013 (vượt giới hạn thiết bị).
 *
 * <p>Dùng {@code revokeTicket} thay vì mật khẩu: BE đã verify mật khẩu đúng
 * trước khi trả 1013, nên vé là đủ để chứng minh danh tính. Vé dùng 1 lần nên
 * không replay được.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevokeDeviceLoginRequest {

    @NotBlank(message = "TOKEN_EMPTY")
    String revokeTicket;

    // Thiết bị user CHỌN để đăng xuất (lấy từ danh sách BE trả kèm lỗi 1013).
    @NotBlank(message = "TOKEN_EMPTY")
    String targetDeviceId;
}
