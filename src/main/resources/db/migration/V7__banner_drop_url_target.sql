-- ============================================================================
-- V7 — Bỏ loại đích 'URL' khỏi `home_banners.target_type`.
-- Banner giờ chỉ dẫn tới thực thể có thật (PRODUCT/CATEGORY/BRAND/FLASH_SALE)
-- để admin chọn từ danh sách thay vì gõ đường dẫn tay. Không còn loại URL thì
-- không còn đường cho link ngoài / javascript: lọt vào banner.
--
-- Bản ghi cũ đang là 'URL' được chuyển sang PRODUCT nếu giá trị trỏ tới sản
-- phẩm có thật, còn lại đưa về FLASH_SALE để không mồ côi dữ liệu; sau đó mới
-- siết enum (đổi enum trước sẽ làm UPDATE trên giá trị ngoài enum thất bại).
-- ============================================================================

UPDATE `home_banners` b
SET b.`target_type` = 'PRODUCT'
WHERE b.`target_type` = 'URL'
  AND EXISTS (SELECT 1 FROM `products` p WHERE p.`id` = b.`target_value`);

UPDATE `home_banners`
SET `target_type` = 'FLASH_SALE'
WHERE `target_type` = 'URL';

ALTER TABLE `home_banners`
  MODIFY COLUMN `target_type` enum('BRAND','CATEGORY','FLASH_SALE','PRODUCT') NOT NULL;
