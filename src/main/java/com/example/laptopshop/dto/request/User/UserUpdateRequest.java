package com.example.laptopshop.dto.request.User;

import java.util.List;

import com.example.laptopshop.validator.PasswordConstraint;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

@Getter
@Setter
public class UserUpdateRequest {
    private String email; // Dùng để validate trùng lặp nếu họ muốn đổi email
    private String fullName;
    private String phone;
    private String address;

    @NotEmpty(message = "USER_ROLES_EMPTY")
    private List<String> roleNames;

    private MultipartFile inputFile; // Nhận ảnh mới nếu họ muốn đổi avatar

}
