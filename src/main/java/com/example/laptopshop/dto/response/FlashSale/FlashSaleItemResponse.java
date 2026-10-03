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

    /** Số suất CÒN LẠI của phiên (sống, tự giảm khi bán). */
    private Integer flashStock;

    /** Số đã bán trong phiên (chỉ tăng). */
    private Integer soldInFlash;

    /** Còn lại trong kho phiên — chính bằng {@code flashStock}. */
    private Integer remainingStock;

    /** null = không giới hạn mỗi khách (D32). */
    private Integer perUserLimit;
}
