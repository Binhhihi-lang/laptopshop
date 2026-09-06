package com.example.laptopshop.dto.request.Client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/auth/forgot-password.
 * Chỉ cần email của user.
 */
@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    @Email(message = "INVALID_EMAIL")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "INVALID_EMAIL")
    private String email;
}
