package com.example.laptopshop.dto.request.Order;

import java.util.List;

import com.example.laptopshop.domain.OrderStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Admin đổi trạng thái nhiều đơn cùng lúc. */
@Getter
@Setter
public class OrderBulkStatusRequest {

    @NotEmpty(message = "INVALID_ORDER_DATA")
    private List<String> ids;

    @NotNull(message = "INVALID_ORDER_STATUS")
    private OrderStatus status;
}
