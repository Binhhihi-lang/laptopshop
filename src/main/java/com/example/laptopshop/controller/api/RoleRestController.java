package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.dto.request.Role.RoleBulkDeleteRequest;
import com.example.laptopshop.dto.request.Role.RoleBulkStatusRequest;
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

    // Chỉ ADMIN (có MANAGE_ROLES_PERMISSIONS) mới quản lý được Role & Permission
    private static final String MANAGE = "hasAuthority('MANAGE_ROLES_PERMISSIONS')";

    @GetMapping
    @PreAuthorize(MANAGE)
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        ApiResponse<List<RoleResponse>> response = new ApiResponse<>();
        response.setResult(this.roleService.getAllRoleResponses());
        return response;
    }

    @GetMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ApiResponse<RoleResponse> getRoleById(@PathVariable String id) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.getRoleResponseById(id));
        return response;
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleCreationRequest request) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.handleCreateRole(request));
        return response;
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String id,
            @Valid @RequestBody RoleUpdateRequest request) {
        ApiResponse<RoleResponse> response = new ApiResponse<>();
        response.setResult(this.roleService.handleUpdateRole(id, request));
        return response;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        this.roleService.deleteRoleById(id);
        return new ApiResponse<>();
    }

    // Xóa hàng loạt role (body JSON { ids: [...] }) — xóa mềm nhờ @SQLDelete
    @PostMapping("/bulk-delete")
    @PreAuthorize(MANAGE)
    public ApiResponse<Void> deleteRoles(@Valid @RequestBody RoleBulkDeleteRequest request) {
        this.roleService.deleteRolesByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Các vai trò đã được xóa thành công");
        return response;
    }

    // Kích hoạt/khóa hàng loạt role (body JSON { ids: [...], active: true/false })
    @PatchMapping("/bulk-status")
    @PreAuthorize(MANAGE)
    public ApiResponse<Void> updateRolesActive(@Valid @RequestBody RoleBulkStatusRequest request) {
        this.roleService.updateRolesActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage(request.isActive()
                ? "Các vai trò đã được kích hoạt thành công"
                : "Các vai trò đã được khóa thành công");
        return response;
    }
}
