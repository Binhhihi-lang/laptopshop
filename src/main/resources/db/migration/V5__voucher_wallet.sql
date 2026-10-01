-- ============================================================================
-- V5 — Sprint 3: VÍ VOUCHER (user_vouchers) + nối lại kho phiên flash.
-- Bảng mới + 1 cột mới trên order_detail; không sửa cột cũ.
-- Phải KHỚP 1-1 với entity vì ddl-auto=validate. Quy ước kiểu theo V3/V4:
--   snake_case · enum(...) xếp theo bảng chữ cái · bit(1) · bigint/int · datetime(6)
--   @ManyToOne -> varchar(255) + FOREIGN KEY thật (validate ĐÒI có ràng buộc).
-- ============================================================================

CREATE TABLE IF NOT EXISTS `user_vouchers` (
  `id` varchar(255) NOT NULL,
  `user_id` varchar(255) NOT NULL,
  `coupon_id` varchar(255) NOT NULL,
  `status` enum('AVAILABLE','EXPIRED','USED') NOT NULL,
  `source` enum('BIRTHDAY','CLAIMED','GIFTED','WELCOME') NOT NULL,
  `acquired_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `order_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- Chống claim trùng ở tầng DB, không chỉ ở service (R17).
  UNIQUE KEY `UK_user_vouchers_user_coupon` (`user_id`, `coupon_id`),
  KEY `IDX_user_vouchers_user_status` (`user_id`, `status`),
  CONSTRAINT `FK_user_vouchers_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK_user_vouchers_coupons` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`),
  CONSTRAINT `FK_user_vouchers_orders` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- order_detail nhớ item flash đã trừ kho lúc chốt, để hủy đơn hoàn ĐÚNG suất
-- vào phiên. Trước V5 thông tin này không được lưu nên hủy đơn làm mất suất
-- vĩnh viễn (xem ledger Sprint 2b — mục releaseStock).
--
-- CỐ Ý KHÔNG đặt FOREIGN KEY: flash_sale_items xóa CỨNG khi admin gỡ phiên
-- (xem V4 — item không soft delete), nên FK sẽ chặn admin xóa phiên nào đã có
-- người mua. Cột này là ảnh chụp id lúc chốt đơn; item không còn thì
-- releaseStock chạy 0 dòng, vốn đã là no-op an toàn.
ALTER TABLE `order_detail`
  ADD COLUMN `flash_sale_item_id` varchar(255) DEFAULT NULL;
