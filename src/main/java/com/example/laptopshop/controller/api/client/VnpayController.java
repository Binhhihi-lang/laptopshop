package com.example.laptopshop.controller.api.client;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Client.VnpayCreateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Client.VnpayCreateResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.VnpayService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Cổng thanh toán VNPay cho storefront.
 *
 * - /create: khách đã đăng nhập — dựng URL đưa trình duyệt sang cổng VNPay.
 * - /return: VNPay redirect trình duyệt về — verify chữ ký, cập nhật đơn,
 *   rồi redirect tiếp trang kết quả bên FE. CÔNG KHAI (trình duyệt không có JWT).
 * - /ipn: VNPay báo kết quả từ server — verify + cập nhật (nguồn chính thức),
 *   trả JSON mà VNPay yêu cầu. CÔNG KHAI.
 */
@RestController
@RequestMapping("/api/v1/client/payments/vnpay")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnpayController {

    VnpayService vnpayService;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<VnpayCreateResponse> createPayment(
            @Valid @RequestBody VnpayCreateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<VnpayCreateResponse> response = new ApiResponse<>();
        response.setResult(this.vnpayService.createPayment(
                request.getOrderCode(), currentUserId(jwt), clientIp(httpRequest)));
        return response;
    }

    @GetMapping("/return")
    public ResponseEntity<Void> returnCallback(@RequestParam Map<String, String> params) {
        String redirectUrl = this.vnpayService.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    // IPN chuẩn VNPay gọi bằng POST form-encoded; vẫn nhận GET .
    @RequestMapping(path = "/ipn", method = { RequestMethod.GET, RequestMethod.POST })
    public Map<String, String> ipnCallback(@RequestParam Map<String, String> params) {
        return this.vnpayService.handleIpn(params);
    }

    private String currentUserId(Jwt jwt) {
        String userId = jwt == null ? null : jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    /**
     * IP khách gửi sang VNPay (vnp_IpAddr). Ưu tiên X-Forwarded-For khi chạy
     * sau proxy/nginx; IPv6 loopback (::1) chuẩn hóa về 127.0.0.1 vì VNPay chỉ
     * nhận IPv4.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null || ip.isBlank() || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}