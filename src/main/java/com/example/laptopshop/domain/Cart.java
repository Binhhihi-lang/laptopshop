package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Giỏ hàng của khách hàng đã đăng nhập.
 *
 * 1 User ↔ 1 Cart (cột user_id UNIQUE) — khách chưa đăng nhập thì FE giữ giỏ ở
 * localStorage, khi đăng nhập sẽ gọi POST /api/v1/client/cart/merge để gộp vào
 * giỏ server. Sau khi login, giỏ server là nguồn dữ liệu chuẩn.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // UNIQUE: mỗi user chỉ có đúng 1 giỏ hàng đang hoạt động.
    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // cascade ALL + orphanRemoval: xóa cart thì xóa luôn cart_item con.
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    /**
     * Thêm item vào giỏ và giữ liên kết 2 chiều. Nếu sản phẩm đã có trong giỏ
     * thì cộng dồn số lượng thay vì tạo dòng trùng (tránh duplicate khi merge
     * giỏ guest).
     */
    public void addItem(CartItem item) {
        CartItem existing = this.findItemByProductId(item.getProduct().getId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            return;
        }
        item.setCart(this);
        this.items.add(item);
    }

    public CartItem findItemByProductId(String productId) {
        if (productId == null) {
            return null;
        }
        return this.items.stream()
                .filter(i -> i.getProduct() != null && productId.equals(i.getProduct().getId()))
                .findFirst()
                .orElse(null);
    }

    public void removeItem(CartItem item) {
        this.items.remove(item);
        item.setCart(null);
    }
}
