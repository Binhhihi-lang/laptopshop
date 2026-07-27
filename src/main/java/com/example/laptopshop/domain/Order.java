package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.List;

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

    // 1 order to many order_detail
    @OneToMany(mappedBy = "order")
    List<OrderDetail> orderDetails;

    // phương thức được gọi trước khi lưu đối tượng Order vào cơ sở dữ liệu
    @PrePersist
    protected void onCreate() {
        if (this.orderDate == null) {
            this.orderDate = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

}
