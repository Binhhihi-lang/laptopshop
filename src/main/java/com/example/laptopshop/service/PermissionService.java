package com.example.laptopshop.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.dto.request.Permission.PermissionCreationRequest;
import com.example.laptopshop.dto.request.Permission.PermissionUpdateRequest;
import com.example.laptopshop.dto.response.Permission.PermissionResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.PermissionMapper;
import com.example.laptopshop.repository.PermissionRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class PermissionService {

     PermissionRepository permissionRepository;
     PermissionMapper permissionMapper;


    public Permission getPermissionById(String id) {
        return this.permissionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
    }

    // Dùng cho RoleService khi lookup theo permissionNames (list nhiều permission)
    public Permission getPermissionByName(String name) {
        return this.permissionRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
    }

    public void validatePermissionName(String name, String currentId) {
        String normalized = name.trim();
        boolean exists = currentId == null
                ? this.permissionRepository.existsByNameIgnoreCase(normalized)
                : this.permissionRepository.existsByNameIgnoreCaseAndIdNot(normalized, currentId);
        if (exists) {
            throw new AppException(ErrorCode.PERMISSION_NAME_EXISTED);
        }
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissionResponses() {
        return this.permissionMapper.toResponseList(this.permissionRepository.findAll());
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermissionResponseById(String id) {
        return this.permissionMapper.toResponse(getPermissionById(id));
    }

    @Transactional
    public PermissionResponse handleCreatePermission(PermissionCreationRequest request) {
        validatePermissionName(request.getName(), null);

        Permission newPermission = this.permissionMapper.toEntity(request);
        newPermission.setName(request.getName().trim());

        Permission saved = this.permissionRepository.save(newPermission);
        return this.permissionMapper.toResponse(saved);
    }

    @Transactional
    public PermissionResponse handleUpdatePermission(String id, PermissionUpdateRequest request) {
        Permission existingPermission = getPermissionById(id);
        validatePermissionName(request.getName(), id);

        this.permissionMapper.updateEntity(request, existingPermission);
        existingPermission.setName(request.getName().trim());

        Permission saved = this.permissionRepository.save(existingPermission);
        return this.permissionMapper.toResponse(saved);
    }

    @Transactional
    public void deletePermissionById(String id) {
        this.permissionRepository.delete(getPermissionById(id));
    }
}