package com.example.laptopshop.dto.request.Auth;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
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
 *
 * <p>Hoạt động multi-select: user có thể chọn nhiều thiết bị cần đăng xuất trong
 * cùng một request. BE sẽ lần lượt {@code revokeDevice} từng thiết bị (bỏ qua
 * thiết bị đang xin đăng nhập).
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevokeDeviceLoginRequest {

    @NotEmpty(message = "TOKEN_EMPTY")
    String revokeTicket;

    // Các thiết bị user CHỌN để đăng xuất (lấy từ danh sách BE trả kèm lỗi 1013).
    @NotEmpty(message = "TOKEN_EMPTY")
    List<String> targetDeviceIds;
}
