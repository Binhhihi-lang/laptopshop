-- ============================================================================
-- V12 — Siết 2 enum của `promotions` về đúng những giá trị v1 thực sự dùng.
--
--   `type`          : BUNDLE, GIFT_VOUCHER, PRODUCT_DISCOUNT  →  PRODUCT_DISCOUNT
--   `discount_type` : AMOUNT, FIXED_PRICE, PERCENT, QUANTITY_TIER →  AMOUNT, PERCENT
--
-- VÌ SAO: cả 4 giá trị bị bỏ đều KHÔNG có đường tạo hợp lệ qua ứng dụng và
-- không có nhánh xử lý thật:
--   * `PromotionService` chặn FIXED_PRICE / QUANTITY_TIER khi tạo/sửa.
--   * `PromotionEngine` chưa bao giờ có nhánh cho GIFT_VOUCHER / BUNDLE — nó chỉ
--     đọc `discount_type`, nên một chương trình "tặng voucher" bị hiểu nhầm
--     thành giảm giá theo dòng. Giữ giá trị trong enum = giữ một lựa chọn sai.
--   * FE hardcode `type = PRODUCT_DISCOUNT` (không có ô chọn).
-- Đã xoá khỏi enum Java tương ứng; siết DB cho khớp vì ddl-auto=validate.
--
-- CHUYỂN DỮ LIỆU: về nguyên tắc các giá trị dưới đây phải là 0 dòng vì không
-- tạo được qua app — chỉ có thể do INSERT tay. Vẫn chuyển thay vì xoá để không
-- phá dữ liệu (tiền lệ V7 khi bỏ target_type 'URL'):
--   * `type` GIFT_VOUCHER/BUNDLE → PRODUCT_DISCOUNT
--       Giữ đúng HÀNH VI THỰC TẾ: engine vốn đã xử lý mọi chương trình như
--       PRODUCT_DISCOUNT (nó không đọc `type`).
--   * `discount_type` FIXED_PRICE/QUANTITY_TIER → AMOUNT
--       Chọn AMOUNT vì gần nghĩa nhất (cùng là "số tiền mỗi máy"). ĐÂY LÀ ÉP
--       KIỂU, không phải chuyển đổi tương đương — admin nên rà lại nếu DB có
--       dòng nào rơi vào nhánh này.
--
-- THỨ TỰ BẮT BUỘC: UPDATE trước, MODIFY enum sau. Siết enum trước sẽ làm UPDATE
-- trên giá trị ngoài enum thất bại.
-- ============================================================================

UPDATE `promotions`
SET `type` = 'PRODUCT_DISCOUNT'
WHERE `type` IN ('GIFT_VOUCHER', 'BUNDLE');

UPDATE `promotions`
SET `discount_type` = 'AMOUNT'
WHERE `discount_type` IN ('FIXED_PRICE', 'QUANTITY_TIER');

ALTER TABLE `promotions`
  MODIFY COLUMN `type` enum('PRODUCT_DISCOUNT') NOT NULL,
  MODIFY COLUMN `discount_type` enum('AMOUNT','PERCENT') NOT NULL;
