package com.example.laptopshop.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_detail")
@Getter
@Setter
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private long quantity;

    // Giá BÁN tại thời điểm mua (đã gồm giá flash nếu có) — giữ nguyên dù giá sản phẩm sau này đổi.
    // Long (không phải double) để cả hệ tiền dùng một kiểu, tránh sai số khi cộng trừ (L1/R22).
    private Long price;

    // Tiền giảm của RIÊNG dòng này từ chương trình khuyến mại (snapshot — D2).
    // Long nullable: cột mới thêm vào bảng đã có dữ liệu → đơn cũ = NULL, KHÔNG dùng primitive long (D13/G2).
    private Long discountAmount;

    // Id chương trình khuyến mại đã áp cho dòng (snapshot) — null nếu dòng không được giảm (D2).
    private String promotionId;

    private String productCode; // mã sản phẩm tại thời điểm mua
    private String productName; // tên sản phẩm tại thời điểm mua
    private String productImage; // ảnh sản phẩm tại thời điểm mua

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    // ===== Getter null-safe =====
    // Đơn cũ có discountAmount = NULL (cột mới thêm ở Sprint 1) → trả 0L để chỗ
    // tính tiền không phải check null. Getter thô getDiscountAmount() vẫn giữ
    // nguyên (Lombok sinh) để tầng persistence ghi/đọc đúng giá trị NULL.

    /** Số tiền giảm của dòng, quy NULL về 0. */
    public long getDiscountAmountSafe() {
        return this.discountAmount == null ? 0L : this.discountAmount;
    }

    // Thành tiền thực của dòng = giá × số lượng − giảm giá cấp dòng.
    // Sàn 0: dữ liệu bẩn (giảm > giá trị dòng) không được sinh số âm.
    public long getLineTotal() {
        long p = this.price == null ? 0L : this.price;
        return Math.max(p * this.quantity - getDiscountAmountSafe(), 0L);
    }

}
