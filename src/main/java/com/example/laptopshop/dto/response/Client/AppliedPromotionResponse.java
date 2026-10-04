package com.example.laptopshop.dto.response.Client;

import com.example.laptopshop.domain.PromotionDiscountType;

import lombok.Getter;
import lombok.Setter;

/**
 * 1 promotion đang áp lên giỏ (preview ở màn giỏ/checkout — D14).
 *
 * <p>
 * Chỉ liệt kê promotion thực sự giảm tiền cho giỏ này. Promotion khớp scope
 * nhưng {@code lineDiscount = 0} (ví dụ dòng đã có flash sale — D25) không xuất
 * hiện, để FE không hiển thị "✓" cho ưu đãi không có tác dụng.
 *
 * <p>
 * Gửi kèm {@code discountType}/{@code discountValue} để FE hiện ĐÚNG QUY TẮC
 * chương trình ("10%" hoặc "500K/máy") thay vì số tiền tính ra cho giỏ hiện tại
 * — số tiền đó là kết quả, không phải định nghĩa chương trình, và khách dễ hiểu
 * nhầm (VD chương trình "10%" mà card ghi "giảm 3tr").
 */
@Getter
@Setter
public class AppliedPromotionResponse {

    private String id;
    private String name;
    private String title;

    /** Cách tính: PERCENT (% trên dòng) hoặc AMOUNT (số tiền mỗi máy — D21). */
    private PromotionDiscountType discountType;

    /** Giá trị thô: PERCENT → 1..100; AMOUNT → số tiền mỗi máy. */
    private Long discountValue;

    /** Trần giảm tối đa cho cả đơn; null/≤0 = không trần. */
    private Long maxDiscountAmount;

    /** Đơn tối thiểu để áp dụng; null = không yêu cầu. */
    private Long minOrderValue;

    /** Số lượng tối thiểu MỖI DÒNG; null = không yêu cầu. */
    private Integer minQuantity;

    /** Ngân sách = số đơn tối đa được áp; null/0 = không giới hạn. */
    private Integer usageLimit;

    /** Số tiền promotion này giảm trên toàn giỏ (đã cap theo maxDiscountAmount). */
    private Long discountAmount;
}
