package com.example.laptopshop.dto.request.Client;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body cho PUT /api/v1/client/cart/items/{productId} — đặt số lượng tuyệt đối. */
@Getter
@Setter
public class UpdateCartItemRequest {

    @NotNull(message = "INVALID_CART_QUANTITY")
    @Min(value = 1, message = "INVALID_CART_QUANTITY")
    private Integer quantity;
}
