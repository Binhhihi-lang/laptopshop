-- ============================================================================
-- V4 — Sprint 2b: FLASH SALE + HOME BANNER. 3 bảng mới, không sửa bảng cũ.
-- Phải KHỚP 1-1 với entity vì ddl-auto=validate. Quy ước kiểu theo V3:
--   snake_case · enum(...) xếp theo bảng chữ cái · bit(1) · bigint/int · datetime(6)
--   @ManyToOne -> varchar(255) + FOREIGN KEY thật (validate ĐÒI có ràng buộc).
-- ============================================================================

CREATE TABLE IF NOT EXISTS `flash_sales` (
  `id` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `banner_image` varchar(255) DEFAULT NULL,
  `start_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_flash_sales_active_period` (`active`, `start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `flash_sale_items` (
  `id` varchar(255) NOT NULL,
  `flash_sale_id` varchar(255) NOT NULL,
  `product_id` varchar(255) NOT NULL,
  `flash_price` bigint NOT NULL,
  `flash_stock` int NOT NULL,
  `sold_in_flash` int NOT NULL,
  `per_user_limit` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- Một sản phẩm chỉ xuất hiện MỘT lần trong một phiên.
  UNIQUE KEY `UK_flash_sale_items_sale_product` (`flash_sale_id`, `product_id`),
  KEY `IDX_flash_sale_items_product` (`product_id`),
  CONSTRAINT `FK_flash_sale_items_flash_sales` FOREIGN KEY (`flash_sale_id`) REFERENCES `flash_sales` (`id`),
  CONSTRAINT `FK_flash_sale_items_products` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `home_banners` (
  `id` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `subtitle` varchar(255) DEFAULT NULL,
  `image` varchar(255) NOT NULL,
  `bg_color` varchar(255) DEFAULT NULL,
  `target_type` enum('BRAND','CATEGORY','FLASH_SALE','PRODUCT','URL') NOT NULL,
  `target_value` varchar(255) NOT NULL,
  `sort_order` int NOT NULL,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_home_banners_active_sort` (`active`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- flash_sale_items KHÔNG soft delete (không có deleted_at): item chỉ có nghĩa
-- trong phiên cha. Xóa mềm thì resolvePriceMap vẫn trả nó về → khách mua được
-- giá shock của sản phẩm admin đã gỡ. Xóa item là xóa cứng.
