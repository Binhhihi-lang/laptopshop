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

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true)
    private String orderCode; // mã đơn hàng hiển thị cho khách, ví dụ "DH07114752"

    private double totalPrice; // Tổng tiền
    private double discountAmount; // số tiền thực tế được giảm tại thời điểm đặt hàng (giữ nguyên dù coupon sau
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

    // getter/setter
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount = discountAmount;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public void setCoupon(Coupon coupon) {
        this.coupon = coupon;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<OrderDetail> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<OrderDetail> orderDetails) {
        this.orderDetails = orderDetails;
    }

}
