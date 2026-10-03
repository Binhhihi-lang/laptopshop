-- ============================================================================
-- V15 — order_detail.original_price: snapshot GIÁ GỐC lúc mua.
--
-- Trước đây dòng đơn chỉ lưu `price` (giá đã trả, đã gồm giá flash). Khách mở
-- chi tiết đơn chỉ thấy một con số trơ, không biết dòng đó từng được giảm giá
-- flash bao nhiêu. Thêm `original_price` để FE hiện giá gốc gạch ngang.
--
-- Chỉ set cho đơn MỚI (lúc tạo đơn); đơn cũ = NULL → FE hiện như trước.
-- ============================================================================

ALTER TABLE `order_detail`
  ADD COLUMN `original_price` bigint DEFAULT NULL;
