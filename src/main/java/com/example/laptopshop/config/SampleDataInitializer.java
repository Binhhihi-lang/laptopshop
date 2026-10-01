package com.example.laptopshop.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.repository.CategoryRepository;
import com.example.laptopshop.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seed dữ liệu mẫu (danh mục + sản phẩm) để người mới chạy thấy ngay giao diện
 * có hàng hoá thay vì trang trắng.
 *
 * <p><b>Nguyên tắc an toàn:</b> chỉ chạy khi bảng {@code products} đang RỖNG.
 * Nhờ vậy khi trỏ vào database đã có dữ liệu thật (Aiven dùng chung), seed tự
 * bỏ qua và KHÔNG BAO GIỜ ghi đè dữ liệu thật. Muốn seed lại thì xoá sạch
 * products rồi khởi động lại.
 *
 * <p>Sản phẩm mẫu cố ý để trống trường {@code image}: storefront đã có sẵn nhánh
 * hiển thị icon thay thế khi ảnh rỗng, nên không cần phụ thuộc ảnh ngoài.
 */
@Component
@RequiredArgsConstructor
public class SampleDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void init() {
        // Cổng chặn: DB đã có sản phẩm nghĩa là dữ liệu thật -> không đụng vào.
        if (this.productRepository.count() > 0) {
            log.info(">>> [SAMPLE DATA] Bảng products đã có dữ liệu — bỏ qua seed mẫu.");
            return;
        }

        Category gaming = category("Laptop Gaming",
                "Laptop cấu hình cao, card đồ hoạ rời cho game thủ", 1);
        Category office = category("Laptop Văn phòng",
                "Mỏng nhẹ, pin lâu, phù hợp làm việc và di chuyển", 2);
        Category graphics = category("Laptop Đồ họa",
                "Màn hình màu chuẩn, hiệu năng mạnh cho thiết kế và dựng phim", 3);
        Category student = category("Laptop Sinh viên",
                "Giá hợp lý, đủ dùng cho học tập và giải trí cơ bản", 4);

        List<Product> products = new ArrayList<>();

        products.add(product("LP-GAM-001", "Asus ROG Strix G16 2024", gaming,
                34_990_000L, 39_990_000L, 12, 8, "Asus",
                "Intel Core i7-13650HX", "16GB DDR5", "512GB SSD NVMe",
                "RTX 4060 8GB", "16 inch WUXGA 165Hz", "Windows 11", 2.5, 24));

        products.add(product("LP-GAM-002", "Acer Nitro V 15", gaming,
                21_490_000L, 24_990_000L, 20, 15, "Acer",
                "Intel Core i5-13420H", "16GB DDR5", "512GB SSD NVMe",
                "RTX 4050 6GB", "15.6 inch FHD 144Hz", "Windows 11", 2.4, 12));

        products.add(product("LP-GAM-003", "MSI Katana 15 B13V", gaming,
                26_990_000L, null, 9, 4, "MSI",
                "Intel Core i7-13620H", "16GB DDR5", "1TB SSD NVMe",
                "RTX 4060 8GB", "15.6 inch FHD 144Hz", "Windows 11", 2.25, 24));

        products.add(product("LP-OFF-001", "Dell Latitude 5440", office,
                23_990_000L, 26_500_000L, 14, 6, "Dell",
                "Intel Core i5-1335U", "16GB DDR4", "512GB SSD NVMe",
                "Intel Iris Xe", "14 inch FHD", "Windows 11 Pro", 1.39, 24));

        products.add(product("LP-OFF-002", "Lenovo ThinkPad E14 Gen 5", office,
                19_990_000L, null, 18, 11, "Lenovo",
                "AMD Ryzen 5 7530U", "16GB DDR4", "512GB SSD NVMe",
                "AMD Radeon", "14 inch WUXGA", "Windows 11", 1.41, 24));

        products.add(product("LP-OFF-003", "HP ProBook 450 G10", office,
                18_490_000L, 20_990_000L, 22, 13, "HP",
                "Intel Core i5-1335U", "8GB DDR4", "512GB SSD NVMe",
                "Intel Iris Xe", "15.6 inch FHD", "Windows 11", 1.74, 12));

        products.add(product("LP-GRA-001", "MacBook Pro 14 M3 Pro", graphics,
                52_990_000L, 56_990_000L, 7, 5, "Apple",
                "Apple M3 Pro", "18GB Unified", "512GB SSD",
                "Apple GPU 14-core", "14.2 inch Liquid Retina XDR", "macOS Sonoma", 1.61, 12));

        products.add(product("LP-GRA-002", "Dell XPS 15 9530", graphics,
                45_990_000L, null, 5, 2, "Dell",
                "Intel Core i7-13700H", "16GB DDR5", "1TB SSD NVMe",
                "RTX 4050 6GB", "15.6 inch OLED 3.5K", "Windows 11", 1.86, 24));

        products.add(product("LP-STU-001", "Asus Vivobook 15 OLED", student,
                13_990_000L, 15_990_000L, 30, 21, "Asus",
                "Intel Core i5-12500H", "16GB DDR4", "512GB SSD NVMe",
                "Intel Iris Xe", "15.6 inch OLED FHD", "Windows 11", 1.7, 12));

        products.add(product("LP-STU-002", "Acer Aspire 3 A315", student,
                10_490_000L, 12_490_000L, 25, 17, "Acer",
                "AMD Ryzen 5 7520U", "8GB LPDDR5", "256GB SSD NVMe",
                "AMD Radeon", "15.6 inch FHD", "Windows 11", 1.78, 12));

        this.productRepository.saveAll(products);
        log.info(">>> [SAMPLE DATA] Đã seed {} danh mục và {} sản phẩm mẫu.",
                4, products.size());
    }

    // Tìm theo tên trước khi tạo: nếu DB đã có danh mục cùng tên thì dùng lại,
    // tránh tạo bản trùng khi bảng products rỗng nhưng categories thì không.
    private Category category(String name, String description, int displayOrder) {
        return this.categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName(name);
                    c.setDescription(description);
                    c.setDisplayOrder(displayOrder);
                    c.setActive(true);
                    return this.categoryRepository.save(c);
                });
    }

    private Product product(String code, String name, Category category,
            long price, Long originalPrice, long quantity, long sold, String factory,
            String cpu, String ram, String storage, String gpu, String screen,
            String os, double weight, int warrantyMonths) {

        Product p = new Product();
        p.setCode(code);
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setOriginalPrice(originalPrice);
        p.setQuantity(quantity);
        p.setSold(sold);
        p.setFactory(factory);
        p.setCpu(cpu);
        p.setRam(ram);
        p.setStorage(storage);
        p.setGpu(gpu);
        p.setScreen(screen);
        p.setOs(os);
        p.setWeight(weight);
        p.setWarrantyMonths(warrantyMonths);
        p.setTarget(category.getName());
        p.setActive(true);
        p.setShortDesc(name + " — " + cpu + " / " + ram + " / " + storage);
        p.setDetailDesc("Sản phẩm mẫu dùng cho môi trường thử nghiệm. "
                + "Cấu hình: " + cpu + ", " + ram + ", " + storage
                + ", " + gpu + ", màn hình " + screen + ".");
        return p;
    }
}
