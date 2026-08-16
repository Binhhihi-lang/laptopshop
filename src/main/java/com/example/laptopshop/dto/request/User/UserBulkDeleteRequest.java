package com.example.laptopshop.dto.request.User;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class UserBulkDeleteRequest {

    @NotEmpty(message = "INVALID_USER_DATA")
    private List<String> ids;

}
