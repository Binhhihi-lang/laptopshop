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
    private Long price; // giá hiện tại (tính lại mỗi lần xem giỏ)
    private Long originalPrice; // giá gạch (null nếu không giảm giá)
    private long quantity; // số lượng trong giỏ
    private Long lineTotal; // price * quantity
    private Long availableQuantity; // tồn kho hiện tại — FE chặn tăng quá số này
}
