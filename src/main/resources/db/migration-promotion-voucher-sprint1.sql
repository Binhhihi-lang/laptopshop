-- ============================================================================
-- BACKFILL Sprint 1 — module Promotion + Voucher
-- Mục đích: `ddl-auto=update` chỉ THÊM cột (nullable), KHÔNG backfill dữ liệu.
--           Đơn cũ sẽ có cột mới = NULL → phải chạy file này 1 lần.
--
-- Thứ tự bắt buộc:
--   1. Chạy app 1 lần (`mvn spring-boot:run`) để Hibernate thêm cột mới.
--   2. Dừng app.
--   3. Chạy file này.
--
-- Idempotent: chạy lại nhiều lần không sai (đều có WHERE ... IS NULL).
--   UPDATE orders       SET promotion_discount = 0,
--                           voucher_discount   = COALESCE(discount_amount, 0)
--    WHERE promotion_discount IS NULL;
--   UPDATE order_detail SET discount_amount = 0 WHERE discount_amount IS NULL;
--
-- ⚠️ DB đích là Aiven cloud (mysql-...aivencloud.com) — KHÔNG tự động chạy.
--    Cần người dùng xác nhận trước khi thực thi (xem ledger Sprint 1, bước 7).
-- ============================================================================

-- B1. orders: đơn cũ chưa tách nguồn giảm giá.
--     `discount_amount` cũ = tổng giảm (chỉ có coupon) → gán hết vào voucher_discount.
--     promotion_discount = 0 vì trước Sprint 1 chưa hề có promotion.
UPDATE orders
   SET promotion_discount = 0,
       voucher_discount   = COALESCE(discount_amount, 0)
 WHERE promotion_discount IS NULL;

-- B2. order_detail: chưa có giảm giá cấp dòng trước Sprint 1.
UPDATE order_detail
   SET discount_amount = 0
 WHERE discount_amount IS NULL;

-- B3. Kiểm tra sau khi chạy: cả 2 truy vấn phải trả 0.
-- SELECT COUNT(*) AS orders_chua_backfill
--   FROM orders WHERE promotion_discount IS NULL OR voucher_discount IS NULL;
-- SELECT COUNT(*) AS order_detail_chua_backfill
--   FROM order_detail WHERE discount_amount IS NULL;
