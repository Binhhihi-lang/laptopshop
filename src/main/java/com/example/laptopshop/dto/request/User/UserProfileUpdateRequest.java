package com.example.laptopshop.dto.request.User;

import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO cập nhật HỒ SƠ CÁ NHÂN của chính người dùng đang đăng nhập (/me).
 * Cho phép đổi: họ tên, email, số điện thoại, địa chỉ và ảnh đại diện.
 *
 * <p>
 * Đổi email ĐƯỢC phép nhưng phải qua {@code validateEmail(email, userId)} —
 * trùng với chính mình thì bỏ qua, trùng người khác thì chặn.
 * KHÔNG bao gồm roleNames / active / password — user không được tự nâng quyền.
 */
@Getter
@Setter
public class UserProfileUpdateRequest {
    private String fullName;
    private String email; // Cho phép đổi email đăng nhập (validate trùng ở service)
    private String phone;
    private String address;
    // Địa chỉ 2 cấp sau sáp nhập 2025 — code + name
    private String provinceCode;
    private String provinceName;
    private String communeCode;
    private String communeName;  //  lần sau mở lại hồ sơ, dropdown Phường/Xã tự chọn đúng phường đó;
    private MultipartFile inputFile; // Ảnh đại diện mới (nếu muốn đổi)
}
