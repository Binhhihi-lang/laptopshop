package com.example.laptopshop.dto.request.User;

import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO cập nhật HỒ SƠ CÁ NHÂN của chính người dùng đang đăng nhập (/me).
 * Chỉ cho phép đổi các trường không ảnh hưởng đến bảo mật/phân quyền:
 * họ tên, số điện thoại, địa chỉ và ảnh đại diện.
 * KHÔNG bao gồm email / roleNames / active / password — STAFF (hay bất kỳ
 * user nào) không được tự ý đổi email hay vai trò của chính mình.
 */
@Getter
@Setter
public class UserProfileUpdateRequest {
    private String fullName;
    private String phone;
    private String address;
    private MultipartFile inputFile; // Ảnh đại diện mới (nếu muốn đổi)
}
