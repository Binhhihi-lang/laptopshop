-- Bỏ hẳn khái niệm "không giới hạn" của trần mỗi khách trong phiên flash sale.
--
-- Trước đây per_user_limit NULL = không giới hạn. Đó là lỗ hổng: suất còn lại 3
-- mà không đặt trần thì MỘT khách ôm hết cả 3 máy ở giá sốc, khách khác không
-- mua được. Nay trần là BẮT BUỘC: phiên cũ để trống được quy về 1 (an toàn nhất),
-- rồi siết cột thành NOT NULL để không còn đường nào tạo ra "vô hạn".

UPDATE flash_sale_items SET per_user_limit = 1 WHERE per_user_limit IS NULL;

ALTER TABLE flash_sale_items MODIFY COLUMN per_user_limit INT NOT NULL;
