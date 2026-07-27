package com.example.laptopshop.dto.request.Role;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

@Getter

public class RoleCreationRequest {
    @NotBlank(message = "ROLE_NAME_EMPTY")
    private String name;

    private String description;

    // Form-data gửi nhiều field cùng tên "permissionNames" (checkbox nhiều lựa
    // chọn), Spring tự bind thành List<String> — y hệt roleNames bên User.
    @NotEmpty(message = "ROLE_PERMISSIONS_EMPTY")
    private List<String> permissionNames;
}