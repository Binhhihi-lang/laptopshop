-- ============================================================================
-- V2 — Backfill các cột tiền mới thêm ở Sprint 1 cho dữ liệu ĐÃ TỒN TẠI.
--
-- VẤN ĐỀ: Flyway tạo cột mới với giá trị NULL cho mọi dòng cũ. Code đọc 2 cột
-- này để tách nguồn giảm giá trên màn chi tiết đơn, nên NULL sẽ hiện thành trống
-- hoặc bị tính thiếu trong báo cáo.
--
-- GIÁ TRỊ ĐÚNG cho đơn đặt TRƯỚC Sprint 1:
--   * promotion_discount = 0            (hồi đó chưa hề có chương trình khuyến mại)
--   * voucher_discount   = discount_amount
--       -> discount_amount là cột cũ, trước Sprint 1 CHỈ chứa tiền giảm của mã
--          giảm giá, nên toàn bộ nó chính là voucher_discount.
--   * discountAmount (tổng) giữ nguyên  -> không đổi số tiền của đơn cũ.
--
-- Idempotent: điều kiện IS NULL chỉ khớp những dòng chưa xử lý, nên Flyway chạy
-- lại trên DB khác cũng không ghi đè số đã có.
-- ============================================================================

UPDATE `orders`
   SET `promotion_discount` = 0,
       `voucher_discount`    = COALESCE(`discount_amount`, 0)
 WHERE `promotion_discount` IS NULL;

UPDATE `order_detail`
   SET `discount_amount` = 0
 WHERE `discount_amount` IS NULL;

-- Ghi chú: đơn cũ KHÔNG được điền promotion_id (vẫn để NULL) vì đúng sự thật:
-- chưa dòng nào từng được áp khuyến mại. promotion_id chỉ có giá trị do engine
-- ghi vào ở các đơn đặt từ Sprint 2 trở đi.
