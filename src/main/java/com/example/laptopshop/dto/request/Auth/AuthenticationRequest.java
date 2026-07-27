package com.example.laptopshop.dto.request.Auth;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;

@Getter
public class AuthenticationRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    private String email;

    @NotBlank(message = "USER_PASSWORD_EMPTY")
    private String password;
}
