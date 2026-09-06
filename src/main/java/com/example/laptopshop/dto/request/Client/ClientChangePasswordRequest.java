package com.example.laptopshop.dto.request.Client;

import com.example.laptopshop.validator.PasswordConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/auth/change-password.
 * Yêu cầu @PreAuthorize (user phải đăng nhập).
 */
@Getter
@Setter
public class ClientChangePasswordRequest {

    @NotBlank
    private String oldPassword;

    @PasswordConstraint(min = 6, message = "INVALID_PASSWORD")
    private String newPassword;
}
