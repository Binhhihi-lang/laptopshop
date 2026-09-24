package com.example.laptopshop.dto.request.FlashSale;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Một sản phẩm trong phiên flash sale.
 *
 * <p>
 * {@code flashStock} là kho DÀNH RIÊNG cho phiên, không phải tồn kho của sản
 * phẩm — hai con số này độc lập (D29): bán hết flash thì dòng đó về giá thường,
 * không chặn mua và không trừ vào {@code Product.quantity}.
 */
@Getter
@Setter
public class FlashSaleItemRequest {

    @NotNull(message = "Sản phẩm không được để trống")
    private String productId;

    /** Giá bán trong phiên (₫). Bắt buộc thấp hơn giá thường — D26. */
    @NotNull(message = "Giá flash không được để trống")
    private Long flashPrice;

    @NotNull(message = "Số lượng flash không được để trống")
    private Integer flashStock;

    /**
     * Số máy tối đa mỗi khách mua trong phiên. null = không giới hạn (D32).
     * Flash giá sốc rất dễ bị dân buôn quét, nên khuyến nghị admin luôn đặt.
     */
    private Integer perUserLimit;
}
