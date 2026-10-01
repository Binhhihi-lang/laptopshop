-- ============================================================================
-- V11 — Bỏ giá trị 'GIFT' khỏi enum `vouchers.voucher_type`.
--
-- BỐI CẢNH: `VoucherType` có 3 giá trị PUBLIC / ASSIGNED / GIFT, nhưng GIFT
-- KHÔNG có hành vi riêng ở đâu cả:
--   * `claim()` chỉ cho PUBLIC tự nhận → ASSIGNED và GIFT bị chặn y hệt nhau.
--   * `assignToUsers()` (admin phát đích danh) phục vụ CẢ hai, phân biệt bằng
--     `template.getVoucherType() == PUBLIC ? CLAIMED : GIFTED` — tức GIFT và
--     ASSIGNED ra cùng một kết quả.
--   * UI admin hiển thị hai lựa chọn nhưng chọn cái nào cũng giống nhau.
-- Giữ lại chỉ gây nhầm lẫn "có 3 kiểu phát hành" trong khi thực tế có 2.
--
-- QUAN TRỌNG: KHÔNG đụng `user_vouchers.source` — giá trị GIFTED ở đó là NGUỒN
-- vào ví (khác khái niệm với voucher_type là KIỂU phát hành), vẫn giữ nguyên.
--
-- THỨ TỰ BẮT BUỘC: chuyển dữ liệu GIFT → ASSIGNED TRƯỚC, rồi mới siết enum.
-- Đổi enum trước sẽ làm UPDATE trên giá trị ngoài enum thất bại.
-- ============================================================================

UPDATE `vouchers`
SET `voucher_type` = 'ASSIGNED'
WHERE `voucher_type` = 'GIFT';

ALTER TABLE `vouchers`
  MODIFY COLUMN `voucher_type` enum('ASSIGNED','PUBLIC') DEFAULT NULL;
