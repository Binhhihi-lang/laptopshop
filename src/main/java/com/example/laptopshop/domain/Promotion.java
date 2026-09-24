package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Chương trình khuyến mại — giảm giá CẤP DÒNG sản phẩm, tự áp khi khớp điều kiện,
 * khách không chọn được (khác {@link Coupon}: cấp đơn, khách gõ mã hoặc chọn từ ví).
 *
 * <p>
 * Soft delete + audit theo đúng khuôn {@link Coupon}. {@code @SQLRestriction} làm
 * mọi truy vấn tự động loại bản ghi đã xóa mềm, nên không được quên khi viết
 * repository query mới.
 */
@Entity
@Table(name = "promotions")
@SQLDelete(sql = "UPDATE promotions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Promotion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	/** Tên nội bộ, admin dùng để tìm/hiện trong danh sách. */
	@Column(nullable = false)
	private String name;

	/** Tiêu đề hiển thị cho khách ở overlay "Khuyến mại và ưu đãi". */
	private String title;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PromotionType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PromotionDiscountType discountType;

	/**
	 * Giá trị giảm. Đơn vị phụ thuộc {@link #discountType}: PERCENT thì 1..100,
	 * AMOUNT/FIXED_PRICE thì đơn vị ₫ trên MỖI MÁY (D21). Dùng Long để một hệ
	 * tiền chỉ một kiểu.
	 */
	@Column(nullable = false)
	private Long discountValue;

	/** Trần giảm mà chương trình này được tạo ra trong MỘT đơn. null = không trần. */
	private Long maxDiscountAmount;

	/** Đơn tối thiểu để được áp. null = không yêu cầu. */
	private Long minOrderValue;

	/** Số lượng tối thiểu trên một dòng để được áp. null = không yêu cầu. */
	private Integer minQuantity;

	@Column(nullable = false)
	private LocalDateTime startDate;

	@Column(nullable = false)
	private LocalDateTime endDate;

	private boolean active = true;

	/**
	 * Khi nhiều chương trình cùng khớp một sản phẩm, ưu tiên cao hơn thắng (D5).
	 * Hoà priority thì mức giảm lớn hơn thắng.
	 */
	private Integer priority = 0;

	/**
	 * Cho phép cộng dồn với chương trình khác trên cùng dòng. v1 KHÔNG dùng:
	 * mỗi dòng chỉ nhận 1 ưu đãi tốt nhất. Để sẵn field để về sau mở rộng mà
	 * không đổi bảng.
	 */
	private boolean stackable = false;

	/** Ngân sách tính theo ĐƠN (không theo dòng). null = không giới hạn. */
	private Integer usageLimit;

	/** Số đơn đã được áp. Chỉ {@code PromotionRepository} tăng/giảm bằng UPDATE
	 *  atomic — không bao giờ set trực tiếp từ service. */
	private Integer usedCount = 0;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	/**
	 * Phạm vi áp dụng. Rỗng = áp cho toàn bộ đơn (ALL).
	 *
	 * <p>
	 * {@code cascade = ALL} + {@code orphanRemoval} để lưu promotion kèm scope
	 * trong một lần; engine đọc qua getter này nên phải nằm trong cùng
	 * transaction với promotion.
	 */
	@OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PromotionScope> scopes = new ArrayList<>();

	/** Sản phẩm bị loại trừ khỏi phạm vi trên. Loại trừ thắng scope. */
	@OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PromotionExclude> excludes = new ArrayList<>();

	@PrePersist
	protected void onCreate() {
		if (this.usedCount == null) {
			this.usedCount = 0;
		}
		if (this.priority == null) {
			this.priority = 0;
		}
	}

	/** Còn ngân sách để áp cho đơn mới? null usageLimit = không giới hạn. */
	public boolean hasBudget() {
		return this.usageLimit == null || this.usedCount == null || this.usedCount < this.usageLimit;
	}

	/** Đang trong thời gian chạy, tính tại {@code now}. */
	public boolean isWithinPeriod(LocalDateTime now) {
		return !now.isBefore(this.startDate) && !now.isAfter(this.endDate);
	}
}
