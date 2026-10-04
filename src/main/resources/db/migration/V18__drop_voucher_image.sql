-- ============================================================================
-- V18 — bỏ ảnh đại diện của voucher.
--
-- Ảnh voucher chưa từng được dùng ở đâu trong luồng thực tế (card voucher ở
-- ví khách và overlay giỏ hàng đều dựng từ mã + mệnh giá + điều kiện, không
-- hiển thị ảnh) nên cắt hẳn cho sạch: bỏ cột + bỏ field DTO/entity.
--
-- Ghi chú: nếu DB có sẵn URL Cloudinary cho voucher cũ, các file đó trở thành
-- mồ côi (không còn tham chiếu). Chấp nhận — không ảnh hưởng luồng nghiệp vụ.
-- ============================================================================

ALTER TABLE `vouchers` DROP COLUMN `image`;
