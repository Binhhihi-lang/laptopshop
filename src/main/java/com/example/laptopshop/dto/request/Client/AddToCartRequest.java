package com.example.laptopshop.dto.request.Client;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body cho POST /api/v1/client/cart/items — thêm 1 sản phẩm vào giỏ. */
@Getter
@Setter
public class AddToCartRequest {

    @NotBlank(message = "PRODUCT_NOT_FOUND")
    private String productId;

    @NotNull(message = "INVALID_CART_QUANTITY")
    @Min(value = 1, message = "INVALID_CART_QUANTITY")
    private Integer quantity;
}
