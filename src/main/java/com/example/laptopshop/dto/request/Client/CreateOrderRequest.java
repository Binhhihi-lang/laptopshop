package com.example.laptopshop.dto.request.Client;

import com.example.laptopshop.domain.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/orders — tạo đơn hàng từ giỏ hiện tại.
 *
 * Không nhận danh sách sản phẩm từ client: đơn luôn được dựng lại từ giỏ
 * server để khách không sửa được giá/số lượng qua request.
 */
@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank(message = "INVALID_RECEIVER_NAME")
    private String receiverFullName;

    @NotBlank(message = "INVALID_RECEIVER_PHONE")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "INVALID_RECEIVER_PHONE")
    private String receiverPhone;

    @NotBlank(message = "INVALID_RECEIVER_ADDRESS")
    private String receiverAddress;

    // Địa chỉ 2 cấp sau sáp nhập 2025 — FE gửi code + name lấy từ select
    @NotBlank(message = "INVALID_RECEIVER_PROVINCE")
    private String receiverProvinceCode;
    private String receiverProvinceName;

    @NotBlank(message = "INVALID_RECEIVER_COMMUNE")
    private String receiverCommuneCode;
    private String receiverCommuneName;

    private String note;

    // Mã giảm giá (optional). Không tìm thấy / hết hạn / hết lượt → báo lỗi rõ
    // ràng thay vì âm thầm bỏ qua.
    private String couponCode;

    @NotNull(message = "INVALID_PAYMENT_METHOD")
    private PaymentMethod paymentMethod;
}
