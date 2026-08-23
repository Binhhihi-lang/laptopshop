package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;


import com.example.laptopshop.domain.Permission;

import com.example.laptopshop.dto.response.Permission.PermissionResponse;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}