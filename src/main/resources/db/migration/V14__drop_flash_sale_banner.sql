-- ============================================================================
-- V14 — bỏ ảnh banner của phiên flash sale.
--
-- Ảnh banner chưa từng được hiển thị ở đâu (trang khách dùng gradient cố định,
-- admin chỉ thấy URL dạng chữ) nên cắt hẳn cho sạch: bỏ cột + bỏ field DTO.
-- ============================================================================

ALTER TABLE `flash_sales` DROP COLUMN `banner_image`;
