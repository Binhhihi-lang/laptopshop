package com.example.laptopshop.controller.api.client;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.User.UserProfileUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: hồ sơ cá nhân của customer đang đăng nhập.
 * Tách khỏi {@link com.example.laptopshop.controller.api.UserRestController}
 * (admin) vì:
 * - Path `/api/v1/admin/**` chỉ ADMIN/STAFF được vào — customer sẽ bị 403.
 * - Tách độc lập để dễ bổ sung business rule riêng cho customer (vd: chỉ được
 *   xem đơn của mình, không truy cập READ_USER) trong tương lai.
 *
 * Endpoint:
 *  - GET  /api/v1/client/users/me  → hồ sơ cá nhân (UserResponse)
 *  - PUT  /api/v1/client/users/me  → cập nhật fullName/phone/address/avatar
 *  (KHÔNG cho đổi email/role/active/password)
 */
@RestController
@RequestMapping("/api/v1/client/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientUserController {

    UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.getMyProfile(userId));
        return response;
    }

    @PutMapping(value = "/me", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> updateMyProfile(
            @ModelAttribute UserProfileUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleUpdateMyProfile(userId, request));
        return response;
    }
}
