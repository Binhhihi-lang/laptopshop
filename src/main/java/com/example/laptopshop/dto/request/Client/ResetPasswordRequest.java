package com.example.laptopshop.dto.request.Client;

import com.example.laptopshop.validator.PasswordConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/auth/reset-password.
 * Token lấy từ URL (mail), newPassword do user nhập ở form.
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "TOKEN_EMPTY")
    private String token;

    @PasswordConstraint(min = 6, message = "INVALID_PASSWORD")
    private String newPassword;
}
