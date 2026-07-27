package com.example.laptopshop.dto.request.User;

import java.util.List;

import com.example.laptopshop.validator.PasswordConstraint;
import jakarta.validation.constraints.*;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import lombok.Getter;

@Getter
@Setter
public class UserCreationRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    @Email(message = "INVALID_EMAIL")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "INVALID_EMAIL")
    private String email;

    @PasswordConstraint(min = 6, message = "INVALID_PASSWORD")
    private String password;

    private String fullName;
    private String address;
    private String phone;

    // Đổi từ roleName (String, 1 role) sang roleNames (List<String>, nhiều
    // role). Form-data gửi nhiều field cùng tên "roleNames" (vd checkbox nhiều
    // lựa chọn), Spring tự bind thành List<String>.
    @NotEmpty(message = "USER_ROLES_EMPTY")
    private List<String> roleNames;

    private MultipartFile inputFile; // Hứng file ảnh avatar trực tiếp trong DTO

}