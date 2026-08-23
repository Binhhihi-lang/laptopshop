package com.example.laptopshop.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.dto.response.Permission.PermissionResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.PermissionMapper;
import com.example.laptopshop.repository.PermissionRepository;
import com.example.laptopshop.repository.RoleRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class PermissionService {

    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;
    RoleRepository roleRepository;
    UserService userService;

    // Quyền có tiền tố MANAGE_ là quyền quản trị hệ thống, KHÔNG được khóa/xóa
    // (để tránh tự khóa chính quyền quản trị quyền hạn — tương tự Role bảo vệ ADMIN).
    private static final String MANAGE_PREFIX = "MANAGE_";

    // Dùng cho RoleService khi lookup theo permissionNames (list nhiều permission)
    public Permission getPermissionByName(String name) {
        return this.permissionRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissionResponses() {
        return this.permissionMapper.toResponseList(this.permissionRepository.findAll());
    }

    // Kích hoạt/khóa hàng loạt permission theo danh sách id. KHÔNG cho khóa/vô
    // hiệu hóa quyền hệ thống (tiền tố MANAGE_) để tránh tự khóa quyền quản trị.
    @Transactional
    public void updatePermissionsActive(List<String> ids, boolean active) {
        List<Permission> permissions = this.permissionRepository.findAllById(ids);
        if (permissions.size() != ids.size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        if (!active && permissions.stream().anyMatch(this::isProtectedPermission)) {
            throw new AppException(ErrorCode.PERMISSION_CANNOT_DEACTIVATE);
        }
        // Thu hồi cache quyền của user thuộc các role chứa permission này (Q2)
        List<Role> affectedRoles = this.roleRepository.findDistinctByPermissions_IdIn(ids);
        this.userService.evictUsersOfRoles(affectedRoles);
        permissions.forEach(permission -> permission.setActive(active));
        this.permissionRepository.saveAll(permissions);
    }

    // Quyền có tiền tố MANAGE_ là quyền hệ thống, không được khóa.
    private boolean isProtectedPermission(Permission permission) {
        String name = permission.getName();
        return name != null && name.startsWith(MANAGE_PREFIX);
    }
}
