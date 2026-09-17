package com.example.laptopshop.controller.api.client;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Client.AddToCartRequest;
import com.example.laptopshop.dto.request.Client.MergeCartRequest;
import com.example.laptopshop.dto.request.Client.UpdateCartItemRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Client.CartResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.CartService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: giỏ hàng của khách đã đăng nhập.
 * Đường dẫn: /api/v1/client/cart — tất cả endpoint đều yêu cầu đăng nhập
 * (path `/api/v1/client/**` là permitAll ở tầng filter, nên bảo vệ thật nằm ở
 * @PreAuthorize dưới đây).
 *
 * Khách CHƯA đăng nhập giữ giỏ ở localStorage phía FE; sau khi login gọi
 * POST /merge để gộp vào giỏ server.
 */
@RestController
@RequestMapping("/api/v1/client/cart")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientCartController {

    CartService cartService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> getMyCart(@AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.getMyCart(currentUserId(jwt)));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> addItem(
            @Valid @RequestBody AddToCartRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.addItem(currentUserId(jwt), request));
    }

    @PutMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> updateItem(
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.updateQuantity(currentUserId(jwt), productId, request));
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> removeItem(
            @PathVariable String productId,
            @AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.removeItem(currentUserId(jwt), productId));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> clear(@AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.clear(currentUserId(jwt)));
    }

    @PostMapping("/merge")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CartResponse> merge(
            @Valid @RequestBody MergeCartRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return wrap(this.cartService.mergeGuestCart(currentUserId(jwt), request));
    }

    /** Lấy userId từ claim "userId" trong JWT; thiếu → coi như chưa đăng nhập. */
    private String currentUserId(Jwt jwt) {
        String userId = jwt == null ? null : jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private ApiResponse<CartResponse> wrap(CartResponse result) {
        ApiResponse<CartResponse> response = new ApiResponse<>();
        response.setResult(result);
        return response;
    }
}
