INSERT IGNORE INTO `home_banners` (`id`, `kicker`, `title`, `subtitle`, `image`, `target_type`, `target_value`, `sort_order`, `active`, `created_at`, `updated_at`)
SELECT 'seed-banner-1', 'BỘ SƯU TẬP MỚI', 'Laptop 2026 mới về', 'Cấu hình mạnh cho học tập, làm việc và chơi game', 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=1600&q=80', 'CATEGORY', c.`id`, 0, 1, NOW(), NOW()
FROM (SELECT `id` FROM `categories` WHERE `active` = 1 AND `deleted_at` IS NULL ORDER BY `name` LIMIT 1) c;

INSERT IGNORE INTO `home_banners` (`id`, `kicker`, `title`, `subtitle`, `image`, `target_type`, `target_value`, `sort_order`, `active`, `created_at`, `updated_at`)
VALUES ('seed-banner-2', 'FLASH SALE', 'Giảm giá sốc mỗi ngày', 'Săn deal giá tốt chỉ trong vài tiếng, số lượng có hạn', 'https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=1600&q=80', 'FLASH_SALE', 'seed-flash-1', 1, 1, NOW(), NOW());

INSERT IGNORE INTO `home_banners` (`id`, `kicker`, `title`, `subtitle`, `image`, `target_type`, `target_value`, `sort_order`, `active`, `created_at`, `updated_at`)
SELECT 'seed-banner-3', 'GAMING', 'Chiến thắng mọi trận đấu', 'Card đồ họa rời, màn hình tần số quét cao, tản nhiệt tốt', 'https://images.unsplash.com/photo-1640955014216-75201056c829?auto=format&fit=crop&w=1600&q=80', 'BRAND', UPPER(p.`factory`), 2, 1, NOW(), NOW()
FROM (SELECT `factory` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `factory` IS NOT NULL AND `factory` <> '' GROUP BY `factory` ORDER BY `factory` LIMIT 1) p;

INSERT IGNORE INTO `home_banners` (`id`, `kicker`, `title`, `subtitle`, `image`, `target_type`, `target_value`, `sort_order`, `active`, `created_at`, `updated_at`)
SELECT 'seed-banner-4', 'DOANH NGHIỆP', 'Giải pháp cho văn phòng', 'Bảo hành riêng 24 tháng, hỗ trợ tận nơi cho doanh nghiệp', 'https://images.pexels.com/photos/31145467/pexels-photo-31145467.jpeg?auto=compress&cs=tinysrgb&w=1600', 'CATEGORY', c.`id`, 3, 1, NOW(), NOW()
FROM (SELECT `id` FROM `categories` WHERE `active` = 1 AND `deleted_at` IS NULL ORDER BY `name` LIMIT 1) c;

INSERT IGNORE INTO `home_banners` (`id`, `kicker`, `title`, `subtitle`, `image`, `target_type`, `target_value`, `sort_order`, `active`, `created_at`, `updated_at`)
SELECT 'seed-banner-5', 'TRẢ GÓP 0%', 'Trả góp không giấy tờ', 'Duyệt hồ sơ trong 5 phút, nhận máy ngay tại cửa hàng', 'https://images.pexels.com/photos/11396009/pexels-photo-11396009.jpeg?auto=compress&cs=tinysrgb&w=1600', 'CATEGORY', c.`id`, 4, 1, NOW(), NOW()
FROM (SELECT `id` FROM `categories` WHERE `active` = 1 AND `deleted_at` IS NULL ORDER BY `name` LIMIT 1) c;

INSERT IGNORE INTO `flash_sales` (`id`, `name`, `description`, `banner_image`, `start_at`, `end_at`, `active`, `created_at`, `updated_at`)
VALUES
('seed-flash-1', 'Flash Sale 12:00', 'Giá sốc cho những máy bán chạy nhất, chỉ trong vài tiếng', 'https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=1600&q=80', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), 1, NOW(), NOW()),
('seed-flash-2', 'Flash Sale Tối 19:00', 'Săn deal sau giờ làm, giảm thêm cho đơn hàng lớn', 'https://images.unsplash.com/photo-1600861195091-690c92f1d2cc?auto=format&fit=crop&w=1600&q=80', DATE_ADD(CURDATE(), INTERVAL 19 HOUR), DATE_ADD(CURDATE(), INTERVAL 22 HOUR), 1, NOW(), NOW()),
('seed-flash-3', 'Flash Sale Cuối Tuần', 'Phiên đã kết thúc, sản phẩm tự về giá thường', 'https://images.pexels.com/photos/17489151/pexels-photo-17489151.jpeg?auto=compress&cs=tinysrgb&w=1600', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 1, NOW(), NOW()),
('seed-flash-4', 'Flash Sale Tạm Dừng', 'Phiên đang tạm dừng, bật lại công tắc để hiện giá flash', 'https://images.pexels.com/photos/31145466/pexels-photo-31145466.jpeg?auto=compress&cs=tinysrgb&w=1600', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 0, NOW(), NOW());

INSERT IGNORE INTO `flash_sale_items` (`id`, `flash_sale_id`, `product_id`, `flash_price`, `flash_stock`, `sold_in_flash`, `per_user_limit`)
SELECT UUID(), f.`id`, p.`id`, FLOOR(p.`price` * 0.75), 20, LEAST(18, p.`rn` * 3), 2
FROM `flash_sales` f
JOIN (SELECT `id`, `price`, ROW_NUMBER() OVER (ORDER BY `price` DESC) AS `rn` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `price` > 100000) p
WHERE f.`id` = 'seed-flash-1' AND p.`rn` <= 6;

INSERT IGNORE INTO `flash_sale_items` (`id`, `flash_sale_id`, `product_id`, `flash_price`, `flash_stock`, `sold_in_flash`, `per_user_limit`)
SELECT UUID(), f.`id`, p.`id`, FLOOR(p.`price` * 0.80), 15, 0, 1
FROM `flash_sales` f
JOIN (SELECT `id`, `price`, ROW_NUMBER() OVER (ORDER BY `price` DESC) AS `rn` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `price` BETWEEN 10000000 AND 30000000) p
WHERE f.`id` = 'seed-flash-2' AND p.`rn` <= 5;

INSERT IGNORE INTO `flash_sale_items` (`id`, `flash_sale_id`, `product_id`, `flash_price`, `flash_stock`, `sold_in_flash`, `per_user_limit`)
SELECT UUID(), f.`id`, p.`id`, FLOOR(p.`price` * 0.85), 10, LEAST(10, 4 + p.`rn`), 1
FROM `flash_sales` f
JOIN (SELECT `id`, `price`, ROW_NUMBER() OVER (ORDER BY `price` ASC) AS `rn` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `price` > 5000000) p
WHERE f.`id` = 'seed-flash-3' AND p.`rn` <= 4;

INSERT IGNORE INTO `promotions` (`id`, `name`, `title`, `description`, `type`, `discount_type`, `discount_value`, `max_discount_amount`, `min_order_value`, `min_quantity`, `start_date`, `end_date`, `active`, `priority`, `stackable`, `usage_limit`, `used_count`, `created_at`, `updated_at`)
VALUES
('seed-promo-1', 'Ưu đãi 10% theo thương hiệu', 'Ưu đãi 10% cho thương hiệu nổi bật', 'Tự động áp dụng khi giỏ hàng có sản phẩm thuộc thương hiệu, không cần nhập mã', 'PRODUCT_DISCOUNT', 'PERCENT', 10, 3000000, 5000000, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY), 1, 10, 0, 500, 137, NOW(), NOW()),
('seed-promo-2', 'Giảm thêm 500k mỗi máy', 'Trừ thẳng 500.000 cho mỗi máy', 'Giảm tiền mặt, cộng dồn được với voucher', 'PRODUCT_DISCOUNT', 'AMOUNT', 500000, 2000000, 3000000, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), 1, 20, 1, 300, 64, NOW(), NOW()),
('seed-promo-3', 'Siêu ưu đãi cuối tuần', 'Giảm 20% tối đa 5 triệu', 'Ưu đãi lớn nhất trong tuần, áp dụng cho mọi đơn hàng', 'PRODUCT_DISCOUNT', 'PERCENT', 20, 5000000, 10000000, 2, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), 1, 1, 1, 200, 89, NOW(), NOW()),
('seed-promo-4', 'Ưu đãi mùa hè đã tắt', 'Ưu đãi mùa hè 15%', 'Chương trình đã tạm dừng, bật lại công tắc để áp dụng', 'PRODUCT_DISCOUNT', 'PERCENT', 15, 2000000, 8000000, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 0, 30, 0, 150, 150, NOW(), NOW()),
('seed-promo-5', 'Flash tháng trước', 'Chương trình tháng trước đã kết thúc', 'Lịch sử chương trình đã đóng', 'PRODUCT_DISCOUNT', 'AMOUNT', 300000, 1500000, 5000000, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), 1, 40, 0, 100, 100, NOW(), NOW());

INSERT IGNORE INTO `promotion_scopes` (`id`, `promotion_id`, `target_type`, `target_value`)
SELECT UUID(), pr.`id`, 'BRAND', UPPER(t.`factory`)
FROM `promotions` pr
JOIN (SELECT `factory` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `factory` IS NOT NULL AND `factory` <> '' GROUP BY `factory` ORDER BY `factory` LIMIT 1) t
WHERE pr.`id` = 'seed-promo-1';

INSERT IGNORE INTO `promotion_scopes` (`id`, `promotion_id`, `target_type`, `target_value`)
SELECT UUID(), pr.`id`, 'PRODUCT', p.`id`
FROM `promotions` pr
JOIN (SELECT `id` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `price` > 10000000 ORDER BY `price` DESC LIMIT 1) p
WHERE pr.`id` = 'seed-promo-1';

INSERT IGNORE INTO `promotion_scopes` (`id`, `promotion_id`, `target_type`, `target_value`)
SELECT UUID(), pr.`id`, 'CATEGORY', c.`id`
FROM `promotions` pr
JOIN (SELECT `id` FROM `categories` WHERE `active` = 1 AND `deleted_at` IS NULL ORDER BY `name` LIMIT 1) c
WHERE pr.`id` = 'seed-promo-2';

INSERT IGNORE INTO `promotion_scopes` (`id`, `promotion_id`, `target_type`, `target_value`)
SELECT UUID(), pr.`id`, 'BRAND', UPPER(t.`factory`)
FROM `promotions` pr
JOIN (SELECT `factory` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `factory` IS NOT NULL AND `factory` <> '' GROUP BY `factory` ORDER BY `factory` DESC LIMIT 1) t
WHERE pr.`id` = 'seed-promo-4';

INSERT IGNORE INTO `promotion_excludes` (`id`, `promotion_id`, `product_id`)
SELECT UUID(), pr.`id`, p.`id`
FROM `promotions` pr
JOIN (SELECT `id` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL ORDER BY `price` ASC LIMIT 1) p
WHERE pr.`id` = 'seed-promo-1';

INSERT IGNORE INTO `vouchers` (`id`, `code`, `title`, `description`, `image`, `discount_percent`, `discount_amount`, `max_discount_amount`, `min_order_value`, `per_user_limit`, `usage_limit`, `used_count`, `voucher_type`, `scope_type`, `start_date`, `expiry_date`, `active`, `created_at`, `updated_at`)
VALUES
('seed-voucher-1', 'SEEDWELCOME10', 'Chào mừng khách mới', 'Giảm 10% cho đơn đầu tiên, tối đa 300.000', 'https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80', 10, NULL, 300000, 3000000, 1, 1000, 218, 'PUBLIC', 'ALL', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY), 1, NOW(), NOW()),
('seed-voucher-2', 'SEEDGIAM500K', 'Giảm thẳng 500k', 'Trừ thẳng 500.000 cho mọi đơn từ 5 triệu', 'https://images.unsplash.com/photo-1627560270549-5c77fcde0ed3?auto=format&fit=crop&w=800&q=80', NULL, 500000, NULL, 5000000, 2, 500, 96, 'PUBLIC', 'ALL', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, NOW(), NOW()),
('seed-voucher-3', 'SEEDLAPTOP15', 'Ưu đãi laptop 15%', 'Giảm 15% tối đa 2 triệu cho đơn laptop từ 10 triệu', 'https://images.pexels.com/photos/8569471/pexels-photo-8569471.jpeg?auto=compress&cs=tinysrgb&w=800', 15, NULL, 2000000, 10000000, 1, 300, 41, 'PUBLIC', 'ALL', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), 1, NOW(), NOW()),
('seed-voucher-4', 'SEEDFREESHIP', 'Miễn phí vận chuyển', 'Giảm 50.000 phí giao hàng cho mọi đơn', 'https://images.unsplash.com/photo-1484788984921-03950022c9ef?auto=format&fit=crop&w=800&q=80', NULL, 50000, NULL, 1000000, NULL, 2000, 512, 'PUBLIC', 'ALL', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), 1, NOW(), NOW()),
('seed-voucher-5', 'SEEDCHAOMS', 'Voucher tặng kèm', 'Voucher quà tặng dành cho khách hàng thân thiết', 'https://images.pexels.com/photos/5874513/pexels-photo-5874513.jpeg?auto=compress&cs=tinysrgb&w=800', 20, NULL, 1000000, 7000000, 1, 100, 7, 'ASSIGNED', 'BRAND', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), 1, NOW(), NOW());

INSERT IGNORE INTO `voucher_scopes` (`id`, `voucher_id`, `target_type`, `target_value`)
SELECT UUID(), v.`id`, 'BRAND', UPPER(t.`factory`)
FROM `vouchers` v
JOIN (SELECT `factory` FROM `products` WHERE `active` = 1 AND `deleted_at` IS NULL AND `factory` IS NOT NULL AND `factory` <> '' GROUP BY `factory` ORDER BY `factory` LIMIT 1) t
WHERE v.`id` = 'seed-voucher-5';
