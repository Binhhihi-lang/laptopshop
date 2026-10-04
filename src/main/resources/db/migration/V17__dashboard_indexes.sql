-- ============================================================================
-- V17 — Chỉ mục phục vụ Bảng điều khiển quản trị (docs/ba/06-dashboard.md).
--
-- Số hiệu V17 (không phải V13): nhánh `feature/promotion-voucher` đã dùng
-- V13..V16 và đã áp lên DB dùng chung. Đặt trùng số sẽ làm Flyway báo
-- "checksum mismatch for migration version 13" và app KHÔNG khởi động được.
--
-- Dashboard chạy nhiều truy vấn tổng hợp trên `orders` (doanh thu theo kỳ, chuỗi
-- theo ngày, đếm theo trạng thái) và trên `vouchers` (đang chạy / sắp hết hạn).
-- Không có chỉ mục thì mỗi lần mở Dashboard là một lần quét toàn bảng.
--
-- Chỉ mục là TỐI ƯU, không phải điều kiện đúng đắn: Dashboard vẫn trả đúng kết
-- quả nếu thiếu, chỉ chậm hơn khi dữ liệu lớn.
--
-- KHÔNG sửa các file V1..V16 đã chạy (checksum Flyway).
-- ============================================================================

-- Doanh thu theo kỳ + chuỗi theo ngày + đếm đơn theo trạng thái.
CREATE INDEX `IDX_orders_status_order_date` ON `orders` (`status`, `order_date`);

-- Nhóm dòng chi tiết theo sản phẩm khi tính top bán chạy.
CREATE INDEX `IDX_order_detail_product` ON `order_detail` (`product_id`);

-- Đếm voucher đang chạy / sắp hết hạn.
CREATE INDEX `IDX_vouchers_active_expiry` ON `vouchers` (`active`, `expiry_date`);
