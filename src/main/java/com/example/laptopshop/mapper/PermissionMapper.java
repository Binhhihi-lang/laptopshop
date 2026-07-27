package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.dto.request.Permission.PermissionCreationRequest;
import com.example.laptopshop.dto.request.Permission.PermissionUpdateRequest;
import com.example.laptopshop.dto.response.Permission.PermissionResponse;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "roles", ignore = true)
    Permission toEntity(PermissionCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateEntity(PermissionUpdateRequest request, @MappingTarget Permission entity);

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}