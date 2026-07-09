package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String code; // mã giảm giá, ví dụ "GIAM10"

    // Kiểu dữ liệu int (Nguyên thủy - Primitive): Không bao giờ được phép null. Nếu
    // bạn không gán giá trị, mặc định nó sẽ tự gán bằng 0.

    // Kiểu dữ liệu Integer (Đối tượng - Wrapper Class): Được phép nhận giá trị
    // null.
    private Integer discountPercent; // Phần trăm giảm (0-100), để kiểu Integer để có thể nhận giá trị null

    private Long discountAmount; // Số tiền giảm trực tiếp (ví dụ: 50000), để kiểu Long cho đồng bộ với tiền tệ

    private LocalDateTime expiryDate; // ngày hết hạn sử dụng

    private Integer usageLimit = 100; // số lượt dùng tối đa

    private Integer usedCount = 0; // số lượt đã dùng

    private boolean active = true; // true: còn dùng được, false: đã khóa

    // TODO: t getter/setter
    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(Integer discountPercent) {
        this.discountPercent = discountPercent;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(Integer usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
