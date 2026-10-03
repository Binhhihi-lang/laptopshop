package com.example.laptopshop.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.example.laptopshop.domain.FlashSale;
import com.example.laptopshop.domain.FlashSaleItem;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.repository.FlashSaleItemRepository;
import com.example.laptopshop.repository.FlashSaleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Helper dựng phiên flash sale cho test tích hợp.
 *
 * <p>
 * Là bean để test {@code @SpringBootTest} inject được, thay vì mỗi test class
 * tự dựng lại và tự quên set field bắt buộc (startAt/endAt/soldInFlash).
 */
@Component
@RequiredArgsConstructor
public class FlashSaleTestSupport {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;

    /** Phiên đang chạy (start = -1h, end = +1h) với một sản phẩm. */
    public FlashSale runningSaleFor(Product product, long flashPrice, int flashStock) {
        return saleFor(product, flashPrice, flashStock,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
    }

    /** Phiên với khung giờ chỉ định — dùng để test phiên đã kết thúc. */
    public FlashSale saleFor(Product product, long flashPrice, int flashStock,
            LocalDateTime startAt, LocalDateTime endAt) {
        FlashSale sale = new FlashSale();
        sale.setName("Phiên test");
        sale.setStartAt(startAt);
        sale.setEndAt(endAt);
        sale.setActive(true);

        FlashSaleItem item = new FlashSaleItem();
        item.setFlashSale(sale);
        item.setProduct(product);
        item.setFlashPrice(flashPrice);
        item.setFlashStock(flashStock);
        item.setSoldInFlash(0);
        // Trần mỗi khách BẮT BUỘC (V16) — mặc định bằng suất để không chặn gì;
        // test nào cần siết thì tự setPerUserLimit lại rồi gọi saveItems.
        item.setPerUserLimit(Math.max(1, flashStock));
        sale.getItems().add(item);
        return this.flashSaleRepository.save(sale);
    }

    /** Lưu lại item sau khi test sửa field (vd perUserLimit). */
    public void saveItems(FlashSale sale) {
        sale.getItems().forEach(this.flashSaleItemRepository::save);
    }
}
