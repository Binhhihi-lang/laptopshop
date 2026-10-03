package com.example.laptopshop.dto.response.Client;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/** Chi tiết đầy đủ 1 đơn hàng — dùng cho trang chi tiết đơn của khách. */
@Getter
@Setter
public class OrderDetailResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private Long subtotal; // tổng tiền hàng SAU khi trừ giảm giá cấp DÒNG (chưa trừ voucher)
    /**
     * Tiền hàng GỐC = Σ(giá × số lượng), CHƯA trừ bất kỳ khoản giảm nào.
     *
     * <p>
     * Cần riêng field này vì {@link #subtotal} đã trừ giảm giá cấp dòng — nếu FE
     * hiện {@code subtotal} lên dòng "Tạm tính" rồi trừ tiếp dòng "Giảm giá sản
     * phẩm" thì khoản giảm cấp dòng bị trừ HAI lần, các dòng không cộng lại ra
     * tổng. Dùng số này làm mốc đầu thì phép cộng khớp:
     * {@code totalBeforeDiscount − promotionDiscount − voucherDiscount + shippingFee = totalPrice}.
     */
    private Long totalBeforeDiscount;
    private Long discountAmount; // TỔNG giảm = promotionDiscount + voucherDiscount (D1)
    private Long promotionDiscount; // giảm cấp DÒNG từ khuyến mại (null = đơn cũ trước Sprint 1)
    private Long voucherDiscount; // giảm cấp ĐƠN từ mã/voucher (null = đơn cũ)
    private Long shippingFee;
    private Long totalPrice;

    private String voucherCode; // null nếu đơn không dùng mã

    private String receiverFullName;
    private String receiverPhone;
    private String receiverEmail;
    private String receiverAddress;
    private String receiverProvinceCode;
    private String receiverProvinceName;
    private String receiverCommuneCode;
    private String receiverCommuneName;
    private String note;

    private List<OrderItemResponse> items;

    /**
     * Chương trình khuyến mại đã áp, gộp theo từng chương trình — để khách thấy
     * đơn được giảm nhờ chương trình NÀO, không chỉ tổng tiền. Rỗng với đơn không
     * có khuyến mại hoặc đơn cũ (trước Sprint 1 chưa lưu {@code promotionId}).
     */
    private List<PromotionLine> promotionLines;

    /** Các lần thử thanh toán của đơn (mới nhất trước) — rỗng với đơn COD. */
    private List<PaymentAttemptResponse> payments;

    /**
     * Có được bấm "Thanh toán lại" hay không, và nếu không thì vì sao. FE không
     * tự suy ra rule này để tránh lệch với BE.
     */
    private boolean canRetryPayment;
    private String retryBlockedReason;

    /** 1 dòng sản phẩm trong đơn — dữ liệu đã snapshot tại thời điểm mua. */
    @Getter
    @Setter
    public static class OrderItemResponse {
        private String productId;
        private String productCode;
        private String productName;
        private String productImage;
        private Long price; // giá tại thời điểm mua
        /** Giá gốc lúc mua (chưa trừ flash) — FE gạch ngang khi khác `price`. */
        private Long originalPrice;
        private long quantity;
        private Long lineTotal; // price * quantity
        private Long discountAmount; // giảm từ khuyến mại cho dòng này (0 nếu không)
    }

    /**
     * 1 chương trình khuyến mại đã áp cho đơn — gộp theo {@code promotionId}
     * (một chương trình có thể giảm nhiều dòng nhưng chỉ hiện 1 lần).
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

    /** 1 lần thử thanh toán — dữ liệu cổng trả về đã lưu lại. */
    @Getter
    @Setter
    public static class PaymentAttemptResponse {
        private String id;
        private String txnRef;
        private int attemptNo;
        private PaymentStatus status;
        private Long amount;
        private String responseCode;
        private String transactionNo;
        private String bankCode;
        private LocalDateTime createdAt;
    }
}
