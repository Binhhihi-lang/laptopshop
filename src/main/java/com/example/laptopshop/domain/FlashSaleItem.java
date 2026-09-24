package com.example.laptopshop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Sản phẩm trong một phiên flash sale, kèm giá sốc và kho riêng của phiên. */
@Entity
@Table(name = "flash_sale_items",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_flash_sale_items_sale_product",
                columnNames = { "flash_sale_id", "product_id" }))
@Getter
@Setter
public class FlashSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flash_sale_id", nullable = false)
    private FlashSale flashSale;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Phải < {@code product.price} khi lưu (D26). */
    @Column(nullable = false)
    private Long flashPrice;

    /** Kho tạm của phiên, tách khỏi {@code Product.quantity}. */
    @Column(nullable = false)
    private Integer flashStock;

    /** Chỉ đổi qua UPDATE atomic ở repository (D29), không set từ service. */
    @Column(nullable = false)
    private Integer soldInFlash = 0;

    /** Tối đa mỗi khách trong phiên; null = không giới hạn (D32). */
    private Integer perUserLimit;

    @PrePersist
    protected void onCreate() {
        if (this.soldInFlash == null) {
            this.soldInFlash = 0;
        }
    }

    public boolean hasFlashStock() {
        return this.soldInFlash == null
                || this.flashStock == null
                || this.soldInFlash < this.flashStock;
    }

    /** Số máy còn lại của kho phiên — FE vẽ "Đã bán x/y". */
    public int getRemainingFlashStock() {
        int stock = this.flashStock == null ? 0 : this.flashStock;
        int sold = this.soldInFlash == null ? 0 : this.soldInFlash;
        return Math.max(0, stock - sold);
    }
}
