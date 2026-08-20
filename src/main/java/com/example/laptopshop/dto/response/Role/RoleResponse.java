package com.example.laptopshop.dto.response.Role;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleResponse {
    private String id;
    private String name;
    private String description;
    private List<String> permissionNames;

    private boolean active = true; // true: đang dùng, false: bị khóa
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
