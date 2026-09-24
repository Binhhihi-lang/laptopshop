package com.example.laptopshop.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test các hàm thuần của FlashSale/FlashSaleItem — không cần Spring context.
 * Neo theo quyết định: D26 (giá flash phải thấp hơn), D27 (hết kho → về giá
 * thường), D29 (kho phiên atomic), D31 (phiên chạy theo khung giờ).
 */
class FlashSaleDomainTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);

    private FlashSale runningSale() {
        FlashSale sale = new FlashSale();
        sale.setStartAt(NOW.minusHours(1));
        sale.setEndAt(NOW.plusHours(1));
        sale.setActive(true);
        return sale;
    }

    /** {@code stock} để Integer (không phải int) mới biểu diễn được ca null. */
    private FlashSaleItem item(FlashSale sale, Integer stock, int sold, Long flashPrice) {
        FlashSaleItem item = new FlashSaleItem();
        item.setFlashSale(sale);
        item.setFlashStock(stock);
        item.setSoldInFlash(sold);
        item.setFlashPrice(flashPrice);
        return item;
    }

    // ==================================================================
    // D31 — phiên chạy theo khung giờ + công tắc active
    // ==================================================================

    @Test
    @DisplayName("Phiên active + trong khung giờ → running")
    void trongKhungGio_running() {
        assertTrue(runningSale().isRunning(NOW));
    }

    @Test
    @DisplayName("Phiên active nhưng chưa tới giờ → chưa running")
    void chuaToiGio_chuaRunning() {
        FlashSale sale = new FlashSale();
        sale.setStartAt(NOW.plusHours(1));
        sale.setEndAt(NOW.plusHours(2));
        sale.setActive(true);
        assertFalse(sale.isRunning(NOW));
    }

    @Test
    @DisplayName("Phiên active nhưng đã qua giờ → không running")
    void quaGio_khongRunning() {
        FlashSale sale = new FlashSale();
        sale.setStartAt(NOW.minusHours(2));
        sale.setEndAt(NOW.minusHours(1));
        sale.setActive(true);
        assertFalse(sale.isRunning(NOW));
    }

    @Test
    @DisplayName("Phiên trong khung giờ nhưng active=false → không running")
    void trongKhungGioNhungTat_khongRunning() {
        FlashSale sale = runningSale();
        sale.setActive(false);
        assertFalse(sale.isRunning(NOW));
    }

    // ==================================================================
    // D27/D29 — kho phiên: hết kho → về giá thường, không chặn mua
    // ==================================================================

    @Test
    @DisplayName("soldInFlash < flashStock → còn kho")
    void conKhoFlash() {
        FlashSale sale = runningSale();
        assertTrue(item(sale, 10, 5, 1000L).hasFlashStock());
    }

    @Test
    @DisplayName("soldInFlash == flashStock → hết kho, dòng về giá thường (D27)")
    void hetKhoFlash() {
        FlashSale sale = runningSale();
        assertFalse(item(sale, 10, 10, 1000L).hasFlashStock());
    }

    @Test
    @DisplayName("flashStock null → coi như còn kho (an toàn, không NPE)")
    void flashStockNull_vanConKho() {
        FlashSale sale = runningSale();
        assertTrue(item(sale, null, 0, 1000L).hasFlashStock());
    }

    @Test
    @DisplayName("getRemainingFlashStock = flashStock − sold, sàn 0")
    void remainingKhongAm() {
        FlashSale sale = runningSale();
        assertEquals(5, item(sale, 10, 5, 1000L).getRemainingFlashStock());
        assertEquals(0, item(sale, 10, 12, 1000L).getRemainingFlashStock());
    }

    // ==================================================================
    // D26 — giá flash phải thấp hơn giá thường (validate nằm ở service)
    // ==================================================================

    @Test
    @DisplayName("flashPrice có giá trị dương → usable")
    void flashPriceDuong_usable() {
        FlashSale sale = runningSale();
        FlashSaleItem item = item(sale, 10, 0, 1000L);
        assertTrue(item.getFlashPrice() > 0L);
    }

    @Test
    @DisplayName("flashPrice null → không dùng làm giá (fallback giá thường)")
    void flashPriceNull_khongUsable() {
        FlashSale sale = runningSale();
        FlashSaleItem item = item(sale, 10, 0, null);
        assertFalse(item.getFlashPrice() != null && item.getFlashPrice() > 0L);
    }
}
