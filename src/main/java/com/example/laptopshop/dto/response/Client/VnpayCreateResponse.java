package com.example.laptopshop.dto.response.Client;

import lombok.Getter;
import lombok.Setter;

/**
 * Kết quả POST /api/v1/client/payments/vnpay/create — URL đưa trình duyệt
 * sang cổng VNPay. FE redirect nguyên trang tới paymentUrl.
 */
@Getter
@Setter
public class VnpayCreateResponse {

    private String paymentUrl;
    private String orderCode;
}