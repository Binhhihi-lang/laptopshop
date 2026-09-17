package com.example.laptopshop.dto.request.Auth;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Yêu cầu đăng xuất hàng loạt các thiết bị đã chọn (profile / quản lý phiên).
 *
 * <p>Khác {@link RevokeDeviceLoginRequest}: user đã đăng nhập nên xác thực bằng
 * JWT, không cần vé. BE tự lọc thiết bị hiện tại để không cho tự đá chính mình
 * qua endpoint này.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevokeSelectedDevicesRequest {

    @NotEmpty(message = "TOKEN_EMPTY")
    List<String> deviceIds;
}