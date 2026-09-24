-- ============================================================================
-- V3 — Sprint 2: bảng cho module KHUYẾN MẠI (Promotion).
--
-- 3 bảng mới, không sửa bảng cũ:
--   promotions        — chương trình KM (giảm CẤP DÒNG, tự áp, D2/D5)
--   promotion_scopes  — phạm vi áp dụng: danh mục / hãng / sản phẩm (D17/D18)
--   promotion_excludes— sản phẩm bị loại trừ khỏi phạm vi (loại trừ thắng scope)
--
-- Cấu trúc phải KHỚP 1-1 với entity, vì dev/prod chạy
-- spring.jpa.hibernate.ddl-auto=validate — lệnh là Hibernate đối chiếu cột + kiểu
-- với bảng thật, lệch là DỪNG KHỞI ĐỘNG. Quy ước theo V1:
--   * snake_case                 (physical naming strategy của Hibernate)
--   * @Enumerated(STRING)   ->  enum(...) với giá trị SẮP XẾP THEO BẢNG CHỮ CÁI
--                                (Hibernate sinh ra như vậy, không theo thứ tự khai báo)
--   * boolean                 ->  bit(1) NOT NULL
--   * Long / Integer          ->  bigint / int, nullable DEFAULT NULL (D13: null = không giới hạn)
--   * LocalDateTime           ->  datetime(6)
--
-- Dùng CREATE TABLE IF NOT EXISTS giống V1: DB Aiven chưa có 3 bảng này nên sẽ
-- được tạo; nếu chạy lại trên DB đã có bảng thì không báo lỗi.
--
-- FK đặt tên dễ đọc (FK_promotion_scopes_promotions) thay vì hash kiểu
-- FKrws2q0si6... như Hibernate tự sinh. ddl-auto=validate không kiểm tra TÊN
-- ràng buộc (chỉ kiểm tra cột + kiểu) nên khác biệt này an toàn.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `promotions` (
  `id` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `type` enum('BUNDLE','GIFT_VOUCHER','PRODUCT_DISCOUNT') NOT NULL,
  `discount_type` enum('AMOUNT','FIXED_PRICE','PERCENT','QUANTITY_TIER') NOT NULL,
  `discount_value` bigint NOT NULL,
  `max_discount_amount` bigint DEFAULT NULL,
  `min_order_value` bigint DEFAULT NULL,
  `min_quantity` int DEFAULT NULL,
  `start_date` datetime(6) NOT NULL,
  `end_date` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `priority` int DEFAULT NULL,
  `stackable` bit(1) NOT NULL,
  `usage_limit` int DEFAULT NULL,
  `used_count` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- Query tìm ứng viên chạy song song theo thời gian (PromotionRepository):
  -- active + start_date <= now <= end_date.
  KEY `IDX_promotions_active_period` (`active`, `start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `promotion_scopes` (
  `id` varchar(255) NOT NULL,
  `promotion_id` varchar(255) NOT NULL,
  `target_type` enum('ALL','BRAND','CATEGORY','PRODUCT') NOT NULL,
  `target_value` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  -- Engine nạp scope theo promotion nên tìm kiếm luôn theo cột cha.
  KEY `IDX_promotion_scopes_promotion` (`promotion_id`),
  CONSTRAINT `FK_promotion_scopes_promotions` FOREIGN KEY (`promotion_id`) REFERENCES `promotions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `promotion_excludes` (
  `id` varchar(255) NOT NULL,
  `promotion_id` varchar(255) NOT NULL,
  `product_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_promotion_excludes_promotion` (`promotion_id`),
  CONSTRAINT `FK_promotion_excludes_promotions` FOREIGN KEY (`promotion_id`) REFERENCES `promotions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- `product_id` ở trên CÓ Ý KHÔNG tạo FK tới `products`:
-- entity PromotionExclude map nó bằng @Column thường (không phải @ManyToOne),
-- nên Hibernate cũng không mong ràng buộc nào. Nhờ vậy admin xoá/xoá mềm một sản
-- phẩm không bị khoá bởi chương trình khuyến mại cũ còn tham chiếu tới nó.
-- ----------------------------------------------------------------------------
