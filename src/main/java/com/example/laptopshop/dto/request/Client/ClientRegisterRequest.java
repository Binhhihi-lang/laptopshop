package com.example.laptopshop.dto.request.Client;

import com.example.laptopshop.validator.PasswordConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/auth/register.
 * Tách khỏi {@link com.example.laptopshop.dto.request.User.UserCreationRequest}
 * (admin) vì customer không nên — và không được — gửi kèm roleNames. Role mặc
 * định là CUSTOMER sẽ do {@code ClientAuthController} ép trước khi gọi service.
 *
 * <p>Lý do tách DTO (thay vì tái sử dụng UserCreationRequest):
 * <ul>
 *   <li>UserCreationRequest có {@code @NotEmpty} trên roleNames — nếu client
 *       không gửi (đúng nguyên tắc) sẽ vi phạm validation, trả 409 USER_ROLES_EMPTY.</li>
 *   <li>Client không có cách nào chỉnh role, nên field đó vừa thừa vừa gây lỗi.</li>
 * </ul>
 */
@Getter
@Setter
public class ClientRegisterRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    @Email(message = "INVALID_EMAIL")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "INVALID_EMAIL")
    private String email;

    @PasswordConstraint(min = 6, message = "INVALID_PASSWORD")
    private String password;

    @NotBlank(message = "INVALID_USER_DATA")
    private String fullName;

    private String phone;
}
