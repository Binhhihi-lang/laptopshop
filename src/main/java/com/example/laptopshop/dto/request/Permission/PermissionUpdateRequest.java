package com.example.laptopshop.dto.request.Permission;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionUpdateRequest {
    @NotBlank(message = "PERMISSION_NAME_EMPTY")
    private String name;

    private String description;
}