package com.example.laptopshop.dto.response.Client;

import lombok.Getter;
import lombok.Setter;

/** 1 dòng trong giỏ hàng trả về FE. */
@Getter
@Setter
public class CartItemResponse {

    private String id;
    private String productId;
    private String productCode;
    private String productName;
    private String productImage;
    private String factory; // hãng — hiển thị phụ dưới tên sản phẩm
    private String category; // danh mục — hiển thị cùng hãng: "hãng · danh mục"
    private String categoryId; // id danh mục — engine khớp scope CATEGORY (không trả FE)
    private Long price; // giá hiện tại (tính lại mỗi lần xem giỏ)
    private Long originalPrice; // giá gạch (null nếu không giảm giá)
    private Long flashPrice; // giá sốc nếu dòng đang trong phiên flash (D25), null = không
    private long quantity; // số lượng trong giỏ
    private Long lineTotal; // price * quantity (đã dùng flashPrice nếu có)
    private Long lineDiscount; // tiền promotion giảm cho dòng này (D5: best-of 1 promotion/dòng)
    private Long availableQuantity; // tồn kho hiện tại — FE chặn tăng quá số này
}
