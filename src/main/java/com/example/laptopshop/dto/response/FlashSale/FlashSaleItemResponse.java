package com.example.laptopshop.dto.response.FlashSale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một sản phẩm trong phiên flash sale. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlashSaleItemResponse {

    private String id;
    private String productId;
    private String productCode;
    private String productName;
    private String productImage;

    /** Giá trong phiên. */
    private Long flashPrice;

    /** Giá bán thường — FE gạch ngang cạnh {@code flashPrice}. */
    private Long regularPrice;

    private Integer flashStock;
    private Integer soldInFlash;

    /** Còn lại trong kho phiên — cùng {@code flashStock} vẽ progress "Đã bán x/y". */
    private Integer remainingStock;

    /** null = không giới hạn mỗi khách (D32). */
    private Integer perUserLimit;
}
