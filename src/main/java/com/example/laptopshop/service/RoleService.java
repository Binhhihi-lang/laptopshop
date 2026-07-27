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
        return this.roleMapper.toResponse(saved);
    }

    @Transactional
    public void deleteRoleById(String id) {
        this.roleRepository.delete(getRoleById(id));
    }
}