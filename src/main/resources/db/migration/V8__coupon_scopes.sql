-- ============================================================================
-- V8 — Voucher chọn được NHIỀU giá trị phạm vi (coupon_scopes).
--
-- Trước đây `coupons.scope_value` chỉ chứa được 1 giá trị, nên form không thể
-- cho chọn nhiều danh mục/hãng/sản phẩm cùng lúc như mockup. Bảng mới theo
-- đúng khuôn `promotion_scopes` (V3) để hai module dùng chung một cách hiểu.
--
-- Cột cũ `coupons.scope_value` được GIỮ LẠI nhưng ngừng dùng: xoá cột là việc
-- phá huỷ dữ liệu không cần thiết, và entity vẫn map nó để ddl-auto=validate
-- không báo lệch. Dữ liệu cũ được chuyển sang bảng mới ngay dưới đây.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `coupon_scopes` (
  `id` varchar(255) NOT NULL,
  `coupon_id` varchar(255) NOT NULL,
  `target_type` enum('ALL','BRAND','CATEGORY','PRODUCT') NOT NULL,
  `target_value` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_coupon_scopes_coupon` (`coupon_id`),
  CONSTRAINT `FK_coupon_scopes_coupons` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Chuyển phạm vi cũ (1 giá trị) sang bảng mới. Bỏ qua coupon cũ chưa từng khai
-- phạm vi (scope_type NULL = ALL) và coupon thiếu scope_value.
INSERT INTO `coupon_scopes` (`id`, `coupon_id`, `target_type`, `target_value`)
SELECT UUID(), c.`id`, c.`scope_type`, c.`scope_value`
FROM `coupons` c
WHERE c.`scope_type` IS NOT NULL
  AND c.`scope_type` <> 'ALL'
  AND c.`scope_value` IS NOT NULL
  AND c.`scope_value` <> '';
