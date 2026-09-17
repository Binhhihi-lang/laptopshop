package com.example.laptopshop.dto.request.Client;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/cart/merge — gộp giỏ guest (FE giữ ở
 * localStorage) vào giỏ server ngay sau khi đăng nhập.
 *
 * Quy tắc gộp: sản phẩm đã có trong giỏ server thì CỘNG DỒN số lượng (xem
 * {@code CartService.mergeGuestCart}) — không tạo dòng trùng.
 */
@Getter
@Setter
public class MergeCartRequest {

    @Valid
    @NotNull(message = "INVALID_CART_DATA")
    private List<GuestCartItem> items;

    @Getter
    @Setter
    public static class GuestCartItem {

        @NotBlank(message = "PRODUCT_NOT_FOUND")
        private String productId;

        @NotNull(message = "INVALID_CART_QUANTITY")
        @Min(value = 1, message = "INVALID_CART_QUANTITY")
        private Integer quantity;
    }
}
