-- ============================================================================
-- V10 — Bổ sung cột còn thiếu so với mockup admin (đợt rà soát 2026-09-26).
--   1. `vouchers.title` + `vouchers.description`: hai dòng copy hiển thị cho
--      khách trên overlay giỏ hàng. Mockup có ở form lẫn chi tiết, BE chưa có.
--   2. `home_banners.kicker`: nhãn nhỏ in hoa đứng trước tiêu đề slide.
-- Cả hai đều NULL-able nên không phá dữ liệu đang có; thêm cột để entity khớp
-- 1-1 với schema (ddl-auto=validate).
-- ============================================================================

ALTER TABLE `vouchers`
  ADD COLUMN `title` varchar(255) DEFAULT NULL,
  ADD COLUMN `description` varchar(500) DEFAULT NULL;

ALTER TABLE `home_banners`
  ADD COLUMN `kicker` varchar(100) DEFAULT NULL;
