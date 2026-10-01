package com.example.laptopshop.dto.response.Order;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/** Chi tiết đầy đủ 1 đơn hàng — dùng cho trang chi tiết đơn của admin. */
@Getter
@Setter
public class AdminOrderDetailResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String paymentTxnRef; // null với COD

    private Long subtotal; // tổng tiền hàng SAU khi trừ giảm giá cấp DÒNG (chưa trừ voucher)
    /**
     * Tiền hàng GỐC = Σ(giá × số lượng), CHƯA trừ bất kỳ khoản giảm nào.
     *
     * <p>
     * Cần riêng field này vì {@link #subtotal} đã trừ giảm giá cấp dòng — nếu FE
     * hiện {@code subtotal} lên dòng "Tạm tính" rồi trừ tiếp dòng "Giảm giá sản
     * phẩm" thì khoản giảm cấp dòng bị trừ HAI lần, các dòng không cộng lại ra
     * tổng. Dùng số này làm mốc đầu thì phép cộng khớp.
     */
    private Long totalBeforeDiscount;
    private Long discountAmount;
    private Long shippingFee;
    private Long totalPrice;

    // D1/G10: tách 2 nguồn giảm để admin đối soát được tiền đến từ đâu.
    private Long promotionDiscount;
    private Long voucherDiscount;
    /** Tên các chương trình khuyến mại đã áp — gộp theo promotionId của từng dòng. */
    private List<PromotionLine> promotionLines;

    private String voucherCode; // null nếu đơn không dùng mã

    // ===== Khách hàng =====
    private String userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // ===== Người nhận =====
    private String receiverFullName;
    private String receiverPhone;
    private String receiverEmail;
    private String receiverAddress;
    private String note;

    private List<AdminOrderItemResponse> items;

    /** Trạng thái admin có thể chuyển tới từ trạng thái hiện tại. */
    private List<OrderStatus> allowedNextStatuses;

    /** 1 dòng sản phẩm trong đơn — dữ liệu đã snapshot tại thời điểm mua. */
    @Getter
    @Setter
    public static class AdminOrderItemResponse {
        private String productId;
        private String productCode;
        private String productName;
        private String productImage;
        private Long price; // giá tại thời điểm mua
        private long quantity;
        private Long lineTotal; // price * quantity
        private Long discountAmount; // giảm từ khuyến mại cho dòng này (0 nếu không)
    }

    /**
     * Một chương trình khuyến mại đã áp cho đơn, gộp theo promotionId.
     * Đơn cũ (trước Sprint 2) không có promotionId → danh sách rỗng.
     */
    @Getter
    @Setter
    public static class PromotionLine {
        private String promotionId;
        /** Tên chương trình; null nếu chương trình đã bị xóa khỏi DB. */
        private String name;
        /** Tổng tiền chương trình này đã giảm cho cả đơn. */
        private Long discountAmount;
    }
}
