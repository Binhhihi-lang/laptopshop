package com.example.laptopshop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Sản phẩm bị loại trừ khỏi một {@link Promotion}.
 *
 * <p>
 * Dùng khi chương trình giảm cả danh mục nhưng muốn chừa vài máy ra (VD: giảm
 * 10% danh mục Laptop, trừ MacBook). Loại trừ thắng scope — sản phẩm nằm trong
 * đây thì không bao giờ được giảm, kể cả khi khớp {@link PromotionScope}.
 *
 * <p>
 * Lưu {@code productId} dạng chuỗi thay vì {@code @ManyToOne Product} để việc
 * xoá sản phẩm không kéo theo ràng buộc khoá ngoại và không làm hỏng chương
 * trình đang chạy.
 */
@Entity
@Table(name = "promotion_excludes")
@Getter
@Setter
public class PromotionExclude {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@ManyToOne
	@JoinColumn(name = "promotion_id", nullable = false)
	private Promotion promotion;

	@Column(name = "product_id", nullable = false)
	private String productId;
}
