package com.example.laptopshop.controller.api.client;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.domain.UserVoucherStatus;
import com.example.laptopshop.dto.request.Client.ValidateVoucherRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Client.VoucherValidationResponse;
import com.example.laptopshop.dto.response.Voucher.VoucherResponse;
import com.example.laptopshop.dto.response.UserVoucher.UserVoucherResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.VoucherService;
import com.example.laptopshop.service.OrderService;
import com.example.laptopshop.service.VoucherWalletService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: mọi thứ về voucher của khách — ví, kho nhận được, và kiểm tra mã ở
 * giỏ. Đường dẫn: /api/v1/client/vouchers
 *
 * <p>
 * Gộp làm một (trước đây tách thành hai controller "voucher" và "voucher") vì cả
 * hai đều phục vụ cùng một mối quan tâm của khách: chọn và dùng voucher. Tách ra
 * chỉ làm hai tên gọi chồng nghĩa.
 *
 * <p>
 * Endpoint validate luôn trả HTTP 200 kèm cờ {@code valid} (kể cả mã sai) để FE
 * hiển thị thông báo inline dưới ô nhập mã, thay vì ném lỗi đỏ.
 */
@RestController
@RequestMapping("/api/v1/client/vouchers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientVoucherController {

    VoucherWalletService voucherWalletService;
    VoucherService voucherService;
    OrderService orderService;

    /** Ví của tôi; {@code status} bỏ trống = cả ví (mọi trạng thái). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UserVoucherResponse>> getMyVouchers(
            @RequestParam(required = false) UserVoucherStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<List<UserVoucherResponse>> response = new ApiResponse<>();
        response.setResult(this.voucherWalletService.getMyVouchers(currentUserId(jwt), status));
        return response;
    }

    /**
     * D16: kho voucher khách có thể claim — chỉ voucher PUBLIC còn nhận được.
     * Trang ví dùng danh sách này để vẽ mục "Ưu đãi dành cho bạn".
     */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<VoucherResponse>> getClaimableVouchers() {
        ApiResponse<List<VoucherResponse>> response = new ApiResponse<>();
        response.setResult(this.voucherService.getClaimableVouchers());
        return response;
    }

    /** Bấm "Lưu mã" — sinh voucher trong ví từ một voucher PUBLIC. */
    @PostMapping("/claim/{voucherId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserVoucherResponse> claim(@PathVariable String voucherId,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<UserVoucherResponse> response = new ApiResponse<>();
        response.setResult(this.voucherWalletService.claim(currentUserId(jwt), voucherId));
        return response;
    }

    /**
     * Kiểm tra mã ở trang giỏ. Logic ưu tiên discountAmount / discountPercent,
     * phạm vi áp dụng và các điều kiện hết hạn / hết lượt nằm ở BE — FE tính lại
     * sẽ lệch số so với lúc đặt hàng thật.
     */
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<VoucherValidationResponse> validate(@Valid @RequestBody ValidateVoucherRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<VoucherValidationResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.validateVoucher(currentUserId(jwt), request));
        return response;
    }

    /** Lấy userId từ claim "userId" trong JWT; thiếu → coi như chưa đăng nhập. */
    private String currentUserId(Jwt jwt) {
        String userId = jwt == null ? null : jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
