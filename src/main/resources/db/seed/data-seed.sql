-- Seed chuẩn LaptopShop: Category + Product (phân trang thử nghiệm)
-- Ảnh dùng URL công khai unsplash ổn định, hiển thị đúng không lỗi

INSERT IGNORE INTO categories (id, name, description, image, display_order, active, created_at, updated_at) VALUES
('CAT-001','Laptop Gaming','Cấu hình mạnh cho game','https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400&q=80',1,1,NOW(),NOW()),
('CAT-002','Laptop Văn phòng','Nhẹ, pin lâu, phù hợp công việc','https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=400&q=80',2,1,NOW(),NOW()),
('CAT-003','Laptop Đồ họa','Màn hình chuẩn màu, GPU chuyên nghiệp','https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=400&q=80',3,1,NOW(),NOW()),
('CAT-004','Laptop Student','Giá tốt, đủ dùng học tập','https://images.unsplash.com/photo-1593642632823-8f78536788c6?w=400&q=80',4,1,NOW(),NOW());

INSERT IGNORE INTO products (id, code, name, image, factory, cpu, ram, storage, gpu, screen, os, price, warranty_months, category_id, active, created_at, updated_at) VALUES
('P-001','LP-G100','Asus ROG Strix G15','https://images.unsplash.com/photo-1587831990711-23ca6441447b?w=600&q=80','Asus','AMD Ryzen 9 7945HX','32GB DDR5','1TB SSD NVMe','RTX 4060','15.6" 144Hz','Windows 11',34990000,24,'CAT-001',1,NOW(),NOW()),
('P-002','LP-G101','MSI Sword 15','https://images.unsplash.com/photo-1611186871348-b1a104d2bca0?w=600&q=80','MSI','Intel i7-13620H','16GB DDR5','512GB SSD','RTX 3050','15.6" 144Hz','Windows 11',21990000,24,'CAT-001',1,NOW(),NOW()),
('P-003','LP-G102','Razer Blade 15','https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&q=80','Razer','Intel i9-13900H','32GB DDR5','2TB SSD','RTX 4070','15.6" 240Hz','Windows 11',54990000,12,'CAT-001',1,NOW(),NOW()),
('P-004','LP-G103','Acer Predator Helios','https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&q=80','Acer','Intel i7-13700H','16GB DDR5','1TB SSD','RTX 4060','17.3" 144Hz','Windows 11',29990000,24,'CAT-001',1,NOW(),NOW()),
('P-005','LP-G104','Lenovo Legion 5 Pro','https://images.unsplash.com/photo-1587831990711-23ca6441447b?w=600&q=80','Lenovo','AMD Ryzen 7 7745HX','16GB DDR5','1TB SSD','RTX 4060','16" 165Hz','Windows 11',27990000,24,'CAT-001',1,NOW(),NOW()),
('P-006','LP-G105','Dell Alienware m15','https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&q=80','Dell','Intel i9-13900HK','32GB DDR5','2TB SSD','RTX 4080','15.6" 360Hz','Windows 11',69990000,12,'CAT-001',1,NOW(),NOW()),
('P-007','LP-O100','MacBook Air M3','https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&q=80','Apple','Apple M3','16GB Unified','512GB SSD','Apple 10-core','13.6" Retina','macOS',29990000,12,'CAT-002',1,NOW(),NOW()),
('P-008','LP-O101','Dell Latitude 7430','https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&q=80','Dell','Intel i7-1265U','16GB DDR4','512GB SSD','Intel Iris Xe','14" FHD','Windows 11',18990000,24,'CAT-002',1,NOW(),NOW()),
('P-009','LP-O102','HP Pavilion 15','https://images.unsplash.com/photo-1593642632823-8f78536788c6?w=600&q=80','HP','Intel i5-1335U','8GB DDR4','256GB SSD','Intel Iris Xe','15.6" FHD','Windows 11',12990000,12,'CAT-002',1,NOW(),NOW()),
('P-010','LP-O103','Lenovo ThinkPad T14','https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=600&q=80','Lenovo','AMD Ryzen 7 Pro 7840U','16GB DDR5','512GB SSD','AMD Radeon','14" 2.8K','Windows 11',23990000,24,'CAT-002',1,NOW(),NOW()),
('P-011','LP-O104','ASUS Zenbook 14','https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&q=80','Asus','Intel i7-1360P','16GB LPDDR5','1TB SSD','Intel Iris Xe','14" OLED','Windows 11',20990000,24,'CAT-002',1,NOW(),NOW()),
('P-012','LP-D100','MacBook Pro 16 M3 Max','https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&q=80','Apple','Apple M3 Max','36GB Unified','1TB SSD','Apple 40-core GPU','16.2" Retina','macOS',79990000,12,'CAT-003',1,NOW(),NOW()),
('P-013','LP-D101','MSI Creator Z16','https://images.unsplash.com/photo-1587831990711-23ca6441447b?w=600&q=80','MSI','Intel i9-13900H','32GB DDR5','1TB SSD','RTX 4070','16" 120Hz','Windows 11',44990000,24,'CAT-003',1,NOW(),NOW()),
('P-014','LP-D102','ASUS ProArt P16','https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&q=80','Asus','AMD Ryzen 9 7945HX','32GB DDR5','1TB SSD','RTX 4070','16" 4K','Windows 11',38990000,24,'CAT-003',1,NOW(),NOW()),
('P-015','LP-S100','Acer Swift 3','https://images.unsplash.com/photo-1593642632823-8f78536788c6?w=600&q=80','Acer','Intel i5-13500H','8GB DDR4','512GB SSD','Intel Iris Xe','14" FHD','Windows 11',10990000,12,'CAT-004',1,NOW(),NOW()),
('P-016','LP-S101','Lenovo IdeaPad 3','https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=600&q=80','Lenovo','AMD Ryzen 5 7520U','8GB DDR5','256GB SSD','AMD Radeon','15.6" FHD','Windows 11',8490000,12,'CAT-004',1,NOW(),NOW()),
('P-017','LP-S102','HP 15s','https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&q=80','HP','Intel i3-1215U','8GB DDR4','256GB SSD','Intel UHD','15.6" FHD','Windows 11',7490000,12,'CAT-004',1,NOW(),NOW()),
('P-018','LP-S103','ASUS VivoBook 15','https://images.unsplash.com/photo-1587831990711-23ca6441447b?w=600&q=80','Asus','Intel i5-1335U','8GB DDR4','512GB SSD','Intel Iris Xe','15.6" FHD','Windows 11',9990000,12,'CAT-004',1,NOW(),NOW()),
('P-019','LP-S104','Dell Inspiron 15','https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&q=80','Dell','Intel i5-1335U','16GB DDR4','512GB SSD','Intel Iris Xe','15.6" FHD','Windows 11',11990000,12,'CAT-004',1,NOW(),NOW()),
('P-020','LP-S105','MSI Modern 15','https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=600&q=80','MSI','Intel i7-1360P','16GB DDR5','1TB SSD','Intel Iris Xe','15.6" FHD','Windows 11',15990000,12,'CAT-004',1,NOW(),NOW());
