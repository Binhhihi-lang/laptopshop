package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.dto.request.Permission.PermissionBulkStatusRequest;
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

    // Chỉ đọc danh sách permission (phục vụ màn hình gán quyền Role + quản lý khóa).
    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<List<PermissionResponse>> getAllPermissions() {
        ApiResponse<List<PermissionResponse>> response = new ApiResponse<>();
        response.setResult(this.permissionService.getAllPermissionResponses());
        return response;
    }

    // Khóa/Kích hoạt hàng loạt permission (body JSON { ids: [...], active: true/false }).
    // Giữ lại vì đây là cách thu hồi/cấp lại quyền runtime an toàn: KHÔNG xóa tên
    // permission (tránh "quyền chết"), chỉ bật/tắt cờ active. Quyền hệ thống
    // (tiền tố MANAGE_) được bảo vệ không cho khóa — xem PermissionService.
    @PatchMapping("/bulk-status")
    @PreAuthorize("hasAuthority('MANAGE_ROLES_PERMISSIONS')")
    public ApiResponse<Void> updatePermissionsActive(@Valid @RequestBody PermissionBulkStatusRequest request) {
        this.permissionService.updatePermissionsActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage(request.isActive()
                ? "Các quyền đã được kích hoạt thành công"
                : "Các quyền đã được khóa thành công");
        return response;
    }
}
