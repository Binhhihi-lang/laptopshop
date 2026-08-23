package com.example.laptopshop.dto.request.Permission;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class PermissionBulkStatusRequest {

    @NotEmpty(message = "PERMISSION_BULK_EMPTY")
    private List<String> ids;

    private boolean active;

}
