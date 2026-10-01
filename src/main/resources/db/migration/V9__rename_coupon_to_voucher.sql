-- ============================================================================
-- V9 — Đổi tên Coupon → Voucher ở tầng DB.
-- Migration này đưa DB về cùng một tên gọi.
--
-- KHÔNG sửa V1/V3/V5/V8: Flyway băm nội dung file đã chạy và lưu checksum, sửa
-- file cũ làm checksum lệch → app KHÔNG khởi động được. Mọi thay đổi phải nằm ở
-- file mới.
--
-- Thứ tự BẮT BUỘC: bỏ FK trỏ tới `coupons` TRƯỚC khi đổi tên bảng, nếu không
-- MySQL không cho RENAME khi còn ràng buộc tham chiếu.
--
-- Đặt tên ràng buộc mới theo quy ước FK_<bảng>_<bảng_cha> đang dùng ở V5/V8.
-- Lưu ý: V1 sinh FK bằng hash Hibernate (FKn1d1gkxckw648m2n2d5gx0yx5) nên phải
-- drop theo đúng hash đó.
-- ============================================================================

-- 1) Bỏ FK đang trỏ tới `coupons`
ALTER TABLE `orders`         DROP FOREIGN KEY `FKn1d1gkxckw648m2n2d5gx0yx5`;
ALTER TABLE `user_vouchers`  DROP FOREIGN KEY `FK_user_vouchers_coupons`;
ALTER TABLE `coupon_scopes`  DROP FOREIGN KEY `FK_coupon_scopes_coupons`;

-- 2) Đổi tên bảng
RENAME TABLE `coupons`       TO `vouchers`;
RENAME TABLE `coupon_scopes` TO `voucher_scopes`;

-- 3) Đổi tên cột khoá ngoại cho khớp entity
ALTER TABLE `orders`        CHANGE COLUMN `coupon_id` `voucher_id` varchar(255) DEFAULT NULL;
ALTER TABLE `user_vouchers` CHANGE COLUMN `coupon_id` `voucher_id` varchar(255) NOT NULL;
ALTER TABLE `voucher_scopes` CHANGE COLUMN `coupon_id` `voucher_id` varchar(255) NOT NULL;

-- 4) Đổi tên cột `coupon_type` → `voucher_type` (VoucherType trong entity)
ALTER TABLE `vouchers` CHANGE COLUMN `coupon_type` `voucher_type` enum('ASSIGNED','GIFT','PUBLIC') DEFAULT NULL;

-- 5) Đổi tên index / unique key cho khỏi mang tên cũ.
-- Tách DROP và ADD thành 2 lệnh: gộp trong một ALTER thì MySQL có thể báo
-- trùng khoá khi tên index mới vô tình trùng tên cũ đang chờ xoá.
ALTER TABLE `user_vouchers` DROP INDEX `UK_user_vouchers_user_coupon`;
ALTER TABLE `user_vouchers`
  ADD UNIQUE KEY `UK_user_vouchers_user_voucher` (`user_id`, `voucher_id`);

ALTER TABLE `orders` DROP INDEX `FKn1d1gkxckw648m2n2d5gx0yx5`;
ALTER TABLE `orders` ADD KEY `IDX_orders_voucher` (`voucher_id`);

-- 6) Gắn lại FK với tên mới
ALTER TABLE `orders`
  ADD CONSTRAINT `FK_orders_vouchers` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`);
ALTER TABLE `user_vouchers`
  ADD CONSTRAINT `FK_user_vouchers_vouchers` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`);
ALTER TABLE `voucher_scopes`
  ADD CONSTRAINT `FK_voucher_scopes_vouchers` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`);

-- ============================================================================
-- 7) Đổi tên KEY quyền: *_COUPON → *_VOUCHER
--
-- ĐÂY LÀ PHẦN RỦI RO NHẤT: `@PreAuthorize("hasAuthority('READ_VOUCHER')")` so
-- khớp CHUỖI với `permissions.name`. Nếu bước này sót thì ADMIN/STAFF mất quyền
-- vào module voucher ngay lập tức.
--
-- Đổi TÊN (giữ nguyên id) chứ không xoá-tạo-lại: `role_permissions` trỏ theo
-- permission_id, xoá đi là mất hết liên kết role.
-- ============================================================================

UPDATE `permissions` SET `name` = 'CREATE_VOUCHER' WHERE `name` = 'CREATE_COUPON';
UPDATE `permissions` SET `name` = 'READ_VOUCHER'   WHERE `name` = 'READ_COUPON';
UPDATE `permissions` SET `name` = 'UPDATE_VOUCHER' WHERE `name` = 'UPDATE_COUPON';
UPDATE `permissions` SET `name` = 'DELETE_VOUCHER' WHERE `name` = 'DELETE_COUPON';
