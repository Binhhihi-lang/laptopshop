package com.example.laptopshop.controller.api.client;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Auth.AuthenticationRequest;
import com.example.laptopshop.dto.request.Auth.IntrospectRequest;
import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.dto.request.Client.ClientChangePasswordRequest;
import com.example.laptopshop.dto.request.Client.ForgotPasswordRequest;
import com.example.laptopshop.dto.request.Client.ResetPasswordRequest;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.AuthenticationResponse;
import com.example.laptopshop.dto.response.IntrospectResponse;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.AuthenticationService;
import com.example.laptopshop.service.PasswordResetService;
import com.example.laptopshop.service.UserService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: đăng ký / đăng nhập / refresh / logout / introspect / quên mật khẩu.
 *
 * <p>Đường dẫn: /api/v1/client/auth
 * Bảo mật: permitAll() ở tầng SecurityConfiguration cho cả nhánh client.
 * Endpoint /change-password yêu cầu @PreAuthorize (user phải đăng nhập).
 *
 * <p>Tái sử dụng {@link AuthenticationService} (login/refresh/logout/introspect)
 * — chỉ thay đổi đường dẫn, không thêm logic auth mới.
 */
@RestController
@RequestMapping("/api/v1/client/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientAuthController {

    AuthenticationService authenticationService;
    UserService userService;
    PasswordResetService passwordResetService;

    // 1. Đăng ký khách hàng: tạo User mới + gán role CUSTOMER (do DataInitializer
    // seed sẵn). Tận dụng UserCreationRequest có sẵn (đã có email/password/fullName/phone
    // + validation) — chỉ cần ép roleNames=["CUSTOMER"] trước khi gọi service.
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody UserCreationRequest request) {
        request.setRoleNames(List.of("CUSTOMER"));
        request.setActive(true);
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleCreateUser(request));
        return response;
    }

    // 2. Đăng nhập: dùng lại AuthenticationService.authenticate. AuthenticationRequest
    // có email + password.
    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request) {
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.authenticate(request));
        return response;
    }

    // 3. Refresh access token.
    @PostMapping("/refresh")
    public ApiResponse<AuthenticationResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.refreshToken(request));
        return response;
    }

    // 4. Logout: blacklist access token + xóa refresh token.
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        this.authenticationService.logout(request);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đăng xuất thành công");
        return response;
    }

    // 5. Introspect: check token còn hợp lệ không.
    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request) {
        ApiResponse<IntrospectResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.introspect(request));
        return response;
    }

    // 6. Quên mật khẩu: nhận email -> gửi link reset qua email.
    // Luôn trả về thành công (kể cả email không tồn tại) để tránh email enumeration.
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        this.passwordResetService.requestReset(request.getEmail());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.");
        return response;
    }

    // 7. Đặt lại mật khẩu bằng token.
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        this.passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đặt lại mật khẩu thành công, vui lòng đăng nhập lại.");
        return response;
    }

    // 8. Đổi mật khẩu (user đã đăng nhập).
    // Jwt chứa claim "userId" do AuthenticationService cấp -> lấy trực tiếp.
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ClientChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        this.userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đổi mật khẩu thành công, vui lòng đăng nhập lại.");
        return response;
    }
}
