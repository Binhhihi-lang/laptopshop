package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.dto.request.Permission.PermissionCreationRequest;
import com.example.laptopshop.dto.request.Permission.PermissionUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Permission.PermissionResponse;
import com.example.laptopshop.service.PermissionService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class PermissionRestController {

    PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<List<PermissionResponse>> getAllPermissions() {
        ApiResponse<List<PermissionResponse>> response = new ApiResponse<>();
        response.setResult(this.permissionService.getAllPermissionResponses());
        return response;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<PermissionResponse> getPermissionById(@PathVariable String id) {
        ApiResponse<PermissionResponse> response = new ApiResponse<>();
        response.setResult(this.permissionService.getPermissionResponseById(id));
        return response;
    }

    // Không có upload file nên có thể dùng @RequestBody (JSON) bình thường,
    // nhưng mình để @ModelAttribute cho đồng bộ cách gọi API FormData toàn hệ
    // thống như quyết định đã áp dụng cho Coupon — tùy bạn chọn.
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<PermissionResponse> createPermission(@Valid @RequestBody PermissionCreationRequest request) {
        ApiResponse<PermissionResponse> response = new ApiResponse<>();
        response.setResult(this.permissionService.handleCreatePermission(request));
        return response;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable String id,
            @Valid @RequestBody PermissionUpdateRequest request) {
        ApiResponse<PermissionResponse> response = new ApiResponse<>();
        response.setResult(this.permissionService.handleUpdatePermission(id, request));
        return response;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<Void> deletePermission(@PathVariable String id) {
        this.permissionService.deletePermissionById(id);
        return new ApiResponse<>();
    }
}