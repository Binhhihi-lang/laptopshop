package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.dto.request.Role.RoleCreationRequest;
import com.example.laptopshop.dto.request.Role.RoleUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Role.RoleResponse;
import com.example.laptopshop.service.RoleService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class RoleRestController {

    RoleService roleService;

    @GetMapping
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        ApiResponse<List<RoleResponse>> response = new ApiResponse<>();
        response.setResult(this.roleService.getAllRoleResponses());
        return response;
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> getRoleById(@PathVariable String id) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.getRoleResponseById(id));
        return response;
    }

    @PostMapping
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleCreationRequest request) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.handleCreateRole(request));
        return response;
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String id,
            @Valid @RequestBody RoleUpdateRequest request) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.handleUpdateRole(id, request));
        return response;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        this.roleService.deleteRoleById(id);
        return new ApiResponse<>();
    }
}