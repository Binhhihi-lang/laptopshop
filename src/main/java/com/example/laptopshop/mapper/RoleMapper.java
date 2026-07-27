package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.dto.request.Role.RoleCreationRequest;
import com.example.laptopshop.dto.request.Role.RoleUpdateRequest;
import com.example.laptopshop.dto.response.Role.RoleResponse;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true) // set tay sau trim()
    @Mapping(target = "permissions", ignore = true) // cần lookup Permission thật từ DB, xử lý ở Service
    @Mapping(target = "users", ignore = true)

    Role toEntity(RoleCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "users", ignore = true)
    void updateEntity(RoleUpdateRequest request, @MappingTarget Role entity);

    // permissions (Set<Permission>) -> permissionNames (List<String>) nhờ helper
    // method permissionToName()
    @Mapping(target = "permissionNames", source = "permissions")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    default String permissionToName(Permission permission) {
        return permission.getName();
    }
}