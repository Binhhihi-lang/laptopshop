package com.example.laptopshop.dto.request.Order;

import com.example.laptopshop.domain.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Admin đổi trạng thái 1 đơn hàng. */
@Getter
@Setter
public class OrderStatusUpdateRequest {

    @NotNull(message = "INVALID_ORDER_STATUS")
    private OrderStatus status;
}
