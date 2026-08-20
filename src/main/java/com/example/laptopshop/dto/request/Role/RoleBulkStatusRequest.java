package com.example.laptopshop.dto.request.Role;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class RoleBulkStatusRequest {

    @NotEmpty(message = "ROLE_BULK_EMPTY")
    private List<String> ids;

    private boolean active;

}
