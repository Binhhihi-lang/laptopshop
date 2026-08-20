package com.example.laptopshop.dto.request.Role;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {
    @NotBlank(message = "ROLE_NAME_EMPTY")
    private String name;

    private String description;

    @NotEmpty(message = "ROLE_PERMISSIONS_EMPTY")
    private List<String> permissionNames;

    private boolean active = true;
}