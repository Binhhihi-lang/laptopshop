package com.example.laptopshop.dto.request.Role;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

@Getter
public class RoleCreationRequest {
    @NotBlank(message = "ROLE_NAME_EMPTY") // chỉ dùng cho string
    private String name;

    private String description;

    @NotEmpty(message = "ROLE_PERMISSIONS_EMPTY") // chỉ dùng cho String, Collection (List, Set), Map, Array
    private List<String> permissionNames;

    private boolean active = true; // mặc định role mới được kích hoạt
}