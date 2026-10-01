-- ============================================================================
-- V6 — Bỏ cột `home_banners.bg_color`.
-- Slide banner luôn có ảnh (BE bắt buộc), nền do scrim gradient ở FE lo; cột
-- này chỉ là text tự do admin phải gõ CSS/hex tay mà client không hề đọc.
-- Xóa cột để entity `HomeBanner` khớp 1-1 với schema (ddl-auto=validate).
-- ============================================================================

ALTER TABLE `home_banners` DROP COLUMN `bg_color`;
