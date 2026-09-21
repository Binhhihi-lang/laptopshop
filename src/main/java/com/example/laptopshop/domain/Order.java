package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String orderCode; // mã đơn hàng hiển thị cho khách, ví dụ "DH07114752"

    private Long totalPrice; // Tổng tiền
    private Long discountAmount; // số tiền thực tế được giảm tại thời điểm đặt hàng (giữ nguyên dù coupon sau
                                 // này đổi %)

    // Phí vận chuyển tại thời điểm đặt (0 nếu được miễn phí). Lưu lại để tổng
    // tiền của đơn cũ không thay đổi khi chính sách phí ship sau này đổi.
    private Long shippingFee;

    // ===== Thông tin người nhận (snapshot tại thời điểm đặt) =====
    // Không đọc lại từ User vì khách có thể sửa hồ sơ sau đó — đơn hàng phải
    // giữ đúng địa chỉ đã dùng để giao.
    private String receiverFullName;
    private String receiverPhone;
    private String receiverAddress; // đã ghép sẵn "địa chỉ cụ thể, phường/xã, tỉnh/thành"
    // Địa chỉ 2 cấp sau sáp nhập 2025 — lưu code + name để hiển thị cấu trúc lại
    private String receiverProvinceCode;
    private String receiverProvinceName;
    private String receiverCommuneCode;
    private String receiverCommuneName;
    private String note; // ghi chú giao hàng của khách (nullable)

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    // Mã giao dịch do cổng thanh toán trả về (VNPay vnp_TransactionNo). null với COD.
    private String paymentTxnRef;

    private LocalDateTime orderDate; // ngày giờ đặt hàng, mặc định = thời điểm tạo Order (PrePersist)

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // many order - 1 coupon (có thể null nếu đơn hàng không dùng mã giảm giá)
    @ManyToOne
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // many orders to one user
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 1 order to many order_detail.
    // cascade ALL + orphanRemoval: lưu Order là lưu luôn các dòng chi tiết con
    // trong cùng 1 transaction (trước đây thiếu cascade nên OrderDetail không
    // bao giờ được persist).
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    List<OrderDetail> orderDetails = new ArrayList<>();

    // phương thức được gọi trước khi lưu đối tượng Order vào cơ sở dữ liệu
    @PrePersist
    protected void onCreate() {
        if (this.orderDate == null) {
            this.orderDate = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
        if (this.paymentMethod == null) {
            this.paymentMethod = PaymentMethod.COD;
        }
        if (this.paymentStatus == null) {
            this.paymentStatus = PaymentStatus.PENDING;
        }
        if (this.shippingFee == null) {
            this.shippingFee = 0L;
        }
        if (this.discountAmount == null) {
            this.discountAmount = 0L;
        }
    }

}
