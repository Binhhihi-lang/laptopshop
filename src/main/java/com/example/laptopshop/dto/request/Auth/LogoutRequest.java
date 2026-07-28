package com.example.laptopshop.dto.request.Auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)

public class LogoutRequest {
    @NotBlank(message = "TOKEN_EMPTY")
    String token;
    private String refreshToken; // không bắt buộc (phòng khi FE cũ chưa gửi kịp), có thì thu hồi luôn
}