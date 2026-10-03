-- ============================================================================
-- V13 — flash_stock đổi nghĩa: HẠN MỨC (gốc) -> SỐ CÒN LẠI (sống).
--
-- Trước: flash_stock = tổng suất cam kết, sold_in_flash = đã bán,
--        còn lại = flash_stock - sold_in_flash.
-- Sau:   flash_stock = số suất CÒN LẠI (tự giảm khi bán, như Product.quantity),
--        sold_in_flash = đã bán (như Product.sold). Còn lại = chính flash_stock.
--
-- Nhờ vậy admin sửa phiên nhập thẳng "còn bao nhiêu suất" (giống sửa tồn kho),
-- không phải tự cộng số đã bán. Tránh luôn cảnh báo "suất còn lại" âm.
--
-- Backfill: flash_stock := GREATEST(0, flash_stock - sold_in_flash) cho mọi dòng
-- cũ để giữ NGUYÊN số còn lại đang hiển thị. Dòng đã âm (bán vượt) kẹp về 0.
-- ============================================================================

UPDATE `flash_sale_items`
SET `flash_stock` = GREATEST(0, `flash_stock` - `sold_in_flash`);
