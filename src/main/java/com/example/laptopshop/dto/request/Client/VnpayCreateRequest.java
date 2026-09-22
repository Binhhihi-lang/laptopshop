package com.example.laptopshop.dto.request.Client;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/payments/vnpay/create — tạo URL thanh toán
 * VNPay cho một đơn đã tạo với paymentMethod = VNPAY.
 */
@Getter
@Setter
public class VnpayCreateRequest {

    @NotBlank(message = "INVALID_ORDER_CODE")
    private String orderCode;
}