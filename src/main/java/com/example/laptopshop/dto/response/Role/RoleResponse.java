package com.example.laptopshop.dto.response.Role;

import java.util.List;
import lombok.Getter;

@Getter
public class RoleResponse {
    private String id;
    private String name;
    private String description;
    private List<String> permissionNames;

}