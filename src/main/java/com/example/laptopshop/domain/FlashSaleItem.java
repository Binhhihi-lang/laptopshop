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

    /**
     * Kho phiên CÒN LẠI — sống, tự giảm khi bán (giống {@code Product.quantity}).
     * Không phải hạn mức gốc: muốn "còn 2 suất" thì set thẳng 2.
     */
    @Column(nullable = false)
    private Integer flashStock;

    /** Số đã bán trong phiên — chỉ tăng (giống {@code Product.sold}). */
    @Column(nullable = false)
    private Integer soldInFlash = 0;

    /** Tối đa mỗi khách trong phiên — BẮT BUỘC, luôn ≥ 1 (V16). */
    @Column(nullable = false)
    private Integer perUserLimit;

    @PrePersist
    protected void onCreate() {
        if (this.soldInFlash == null) {
            this.soldInFlash = 0;
        }
    }

    /** Còn suất để bán? {@code flashStock} là số CÒN LẠI nên chỉ cần > 0. */
    public boolean hasFlashStock() {
        return this.flashStock != null && this.flashStock > 0;
    }

    /** Số máy còn lại của kho phiên — chính là {@code flashStock}. */
    public int getRemainingFlashStock() {
        return this.flashStock == null ? 0 : Math.max(0, this.flashStock);
    }
}
