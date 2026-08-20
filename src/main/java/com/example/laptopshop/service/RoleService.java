package com.example.laptopshop.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.dto.request.Role.RoleCreationRequest;
import com.example.laptopshop.dto.request.Role.RoleUpdateRequest;
import com.example.laptopshop.dto.response.Role.RoleResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.RoleMapper;
import com.example.laptopshop.repository.RoleRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@Service
public class RoleService {

     RoleRepository roleRepository;
     PermissionService permissionService;
     RoleMapper roleMapper;
     UserService userService;


    public void validateRoleName(String name, String currentId) {
        String normalized = name.trim();
        boolean exists = currentId == null
                ? this.roleRepository.existsByNameIgnoreCase(normalized)
                : this.roleRepository.existsByNameIgnoreCaseAndIdNot(normalized, currentId);
        if (exists) {
            throw new AppException(ErrorCode.ROLE_NAME_EXISTED);
        }
    }

    // nhiều Permission cùng lúc theo list tên,
    public Set<Permission> getPermissionsByNames(List<String> names) {
        Set<Permission> permissions = new HashSet<>();
        for (String name : names) {
            permissions.add(this.permissionService.getPermissionByName(name));
        }
        return permissions;
    }

    public Role getRoleById(String id) {
        return this.roleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoleResponses() {
        return this.roleMapper.toResponseList(this.roleRepository.findAll());
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleResponseById(String id) {
        return this.roleMapper.toResponse(getRoleById(id));
    }

    @Transactional
    public RoleResponse handleCreateRole(RoleCreationRequest request) {
        validateRoleName(request.getName(), null);

        Role newRole = this.roleMapper.toEntity(request);
        newRole.setName(request.getName().trim());
        newRole.setPermissions(getPermissionsByNames(request.getPermissionNames()));

        Role saved = this.roleRepository.save(newRole);
        return this.roleMapper.toResponse(saved);
    }

    @Transactional
    public RoleResponse handleUpdateRole(String id, RoleUpdateRequest request) {
        Role existingRole = getRoleById(id);
        validateRoleName(request.getName(), id);

        this.roleMapper.updateEntity(request, existingRole);
        existingRole.setName(request.getName().trim());
        existingRole.setPermissions(getPermissionsByNames(request.getPermissionNames()));

        Role saved = this.roleRepository.save(existingRole);
        // Permission của role thay đổi -> thu hồi ngay quyền của mọi user thuộc role
        this.userService.evictUsersOfRoles(List.of(saved));
        return this.roleMapper.toResponse(saved);
    }

    @Transactional
    public void deleteRoleById(String id) {
        Role role = getRoleById(id);
        this.userService.evictUsersOfRoles(List.of(role)); // xóa mềm -> thu hồi quyền user
        this.roleRepository.delete(role);
    }

    // Xóa hàng loạt role theo danh sách id: nhờ @SQLDelete ở Role.java, thao tác
    // này là xóa MỀM (UPDATE deleted_at). Đủ id mới xóa, thiếu id nào -> báo lỗi.
    @Transactional
    public void deleteRolesByIds(List<String> ids) {
        List<Role> roles = this.roleRepository.findAllById(ids);
        if (roles.size() != ids.size()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }
        this.userService.evictUsersOfRoles(roles); // xóa mềm -> thu hồi quyền user trước khi xóa
        this.roleRepository.deleteAll(roles);
    }

    // Kích hoạt/khóa hàng loạt role theo danh sách id. Không cho khóa/vô hiệu
    // hóa vai trò ADMIN (bảo vệ hệ thống khỏi tự khóa quyền quản trị).
    @Transactional
    public void updateRolesActive(List<String> ids, boolean active) {
        List<Role> roles = this.roleRepository.findAllById(ids);
        if (roles.size() != ids.size()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (!active) {
            boolean containsAdmin = roles.stream()
                    .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getName()));
            if (containsAdmin) {
                throw new AppException(ErrorCode.ROLE_CANNOT_DEACTIVATE);
            }
        }
        roles.forEach(role -> role.setActive(active));
        this.roleRepository.saveAll(roles);
        // Khóa/kích hoạt role -> thu hồi ngay quyền của mọi user thuộc các role này (Q2)
        this.userService.evictUsersOfRoles(roles);
    }
}