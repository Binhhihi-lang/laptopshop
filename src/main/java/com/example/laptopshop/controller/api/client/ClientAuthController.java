package com.example.laptopshop.controller.api.client;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Auth.AuthenticationRequest;
import com.example.laptopshop.dto.request.Auth.IntrospectRequest;
import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.dto.request.Auth.RevokeDeviceLoginRequest;
import com.example.laptopshop.dto.request.Auth.RevokeSelectedDevicesRequest;
import com.example.laptopshop.dto.request.Client.ClientChangePasswordRequest;
import com.example.laptopshop.dto.request.Client.ForgotPasswordRequest;
import com.example.laptopshop.dto.request.Client.ResetPasswordRequest;
import com.example.laptopshop.dto.request.Client.ClientRegisterRequest;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.AuthenticationResponse;
import com.example.laptopshop.dto.response.DeviceInfoResponse;
import com.example.laptopshop.dto.response.IntrospectResponse;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.AuthenticationService;
import com.example.laptopshop.service.PasswordResetService;
import com.example.laptopshop.service.UserService;
import com.example.laptopshop.utils.DeviceRequestUtils;

import jakarta.servlet.http.HttpServletRequest;
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
    // seed sẵn). Dùng DTO riêng {@link ClientRegisterRequest} (chỉ email/password/
    // fullName/phone) — client không cần và không được phép gửi roleNames. Map sang
    // {@link UserCreationRequest} nội bộ và ép roleNames=["CUSTOMER"] trước khi gọi
    // service, vì UserCreationRequest dùng chung với admin có @NotEmpty trên
    // roleNames (nếu thiếu sẽ fail validation ở @Valid).
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody ClientRegisterRequest request) {
        UserCreationRequest internal = new UserCreationRequest();
        internal.setEmail(request.getEmail());
        internal.setPassword(request.getPassword());
        internal.setFullName(request.getFullName());
        internal.setPhone(request.getPhone());
        internal.setRoleNames(List.of("CUSTOMER"));
        internal.setActive(true);
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleCreateUser(internal));
        return response;
    }

    // 2. Đăng nhập: dùng lại AuthenticationService.authenticate. AuthenticationRequest
    // có email + password. Kèm nhận diện thiết bị qua header X-Device-Id để áp
    // giới hạn số thiết bị (CUSTOMER tối đa app.device.max-sessions, ADMIN/STAFF miễn).
    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.authenticate(request,
                DeviceRequestUtils.getDeviceId(httpRequest),
                DeviceRequestUtils.getUserAgent(httpRequest),
                DeviceRequestUtils.getClientIp(httpRequest)));
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

    // ================== QUẢN LÝ THIẾT BỊ ĐĂNG NHẬP ==================

    // 9. Đăng xuất THIẾT BỊ ĐÃ CHỌN khi login bị chặn vì vượt giới hạn.
    // Xác thực bằng revokeTicket (BE cấp kèm lỗi 1013) — không cần mật khẩu, vé
    // dùng 1 lần. Đá máy user chọn rồi cấp token cho thiết bị đang xin đăng nhập.
    @PostMapping("/devices/revoke-and-login")
    public ApiResponse<AuthenticationResponse> revokeDeviceAndLogin(
            @Valid @RequestBody RevokeDeviceLoginRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.revokeDeviceAndLogin(
                request.getRevokeTicket(), request.getTargetDeviceIds(),
                DeviceRequestUtils.getUserAgent(httpRequest),
                DeviceRequestUtils.getClientIp(httpRequest)));
        return response;
    }

    // 9b. Đăng xuất nhiều thiết bị đã chọn (profile storefront).
    @PostMapping("/devices/revoke-selected")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeSelectedDevices(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RevokeSelectedDevicesRequest request,
            HttpServletRequest httpRequest) {
        this.authenticationService.revokeSelectedDevices(
                requireUserId(jwt),
                DeviceRequestUtils.getDeviceId(httpRequest),
                request.getDeviceIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đã đăng xuất các thiết bị đã chọn");
        return response;
    }

    // 10. Liệt kê thiết bị đang đăng nhập (trang quản lý thiết bị).
    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DeviceInfoResponse>> listDevices(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        ApiResponse<List<DeviceInfoResponse>> response = new ApiResponse<>();
        response.setResult(this.authenticationService.listDevices(
                requireUserId(jwt), DeviceRequestUtils.getDeviceId(httpRequest)));
        return response;
    }

    // 11. Đăng xuất 1 thiết bị cụ thể.
    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeDevice(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String deviceId) {
        this.authenticationService.revokeDevice(requireUserId(jwt), deviceId);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đã đăng xuất thiết bị");
        return response;
    }

    // 12. Đăng xuất mọi thiết bị KHÁC (giữ thiết bị đang dùng) trong trang phiên đăng nhập
    @PostMapping("/devices/revoke-others")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeOtherDevices(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        this.authenticationService.revokeOtherDevices(
                requireUserId(jwt), DeviceRequestUtils.getDeviceId(httpRequest));
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đã đăng xuất các thiết bị khác");
        return response;
    }

    /** Claim "userId" do AuthenticationService cấp; thiếu -> coi như chưa xác thực. */
    private String requireUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
