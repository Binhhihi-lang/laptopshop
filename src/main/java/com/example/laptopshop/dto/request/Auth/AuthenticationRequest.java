package com.example.laptopshop.dto.request.Auth;

import jakarta.validation.constraints.NotBlank;

public class AuthenticationRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    private String email;

    @NotBlank(message = "USER_PASSWORD_EMPTY")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
