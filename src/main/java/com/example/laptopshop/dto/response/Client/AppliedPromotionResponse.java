package com.example.laptopshop.dto.response.Client;

import lombok.Getter;
import lombok.Setter;

/**
 * 1 promotion đang áp lên giỏ (preview ở màn giỏ/checkout — D14).
 *
 * <p>
 * Chỉ liệt kê promotion thực sự giảm tiền cho giỏ này. Promotion khớp scope
 * nhưng {@code lineDiscount = 0} (ví dụ dòng đã có flash sale — D25) không xuất
 * hiện, để FE không hiển thị "✓" cho ưu đãi không có tác dụng.
 */
@Getter
@Setter
public class AppliedPromotionResponse {

    private String id;
    private String name;
    private String title;

    /** Số tiền promotion này giảm trên toàn giỏ (đã cap theo maxDiscountAmount). */
    private Long discountAmount;
}
