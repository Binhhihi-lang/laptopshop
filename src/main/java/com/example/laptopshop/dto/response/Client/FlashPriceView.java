package com.example.laptopshop.dto.response.Client;

/**
 * Giá flash của MỘT sản phẩm tại MỘT thời điểm (đầu ra resolvePriceMap, §3.1b).
 * Là record nội bộ — FE nhận các trường này nằm trong ProductResponse /
 * CartItemResponse, không xin endpoint riêng.
 */
public record FlashPriceView(
        /** FlashSaleItem.id — cần để trừ kho lúc chốt (D29). */
        String itemId,
        /** Phiên chứa item — FE link tới /flash-sale/{id}. */
        String flashSaleId,
        /** Giá bán trong phiên (₫). */
        Long flashPrice,
        /** Kho dành riêng cho phiên. */
        Integer flashStock,
        /** Đã bán trong phiên — FE vẽ progress "Đã bán x/y". */
        Integer soldInFlash,
        /** Lúc phiên kết thúc — đồng hồ đếm ngược. */
        java.time.LocalDateTime endAt,
        /** Khách còn mua được bao nhiêu máy (D32); null = chưa xét, 0 = hết suất. */
        Integer perUserLimitLeft) {

    /** Dòng này đang có giá flash thật để dùng thay giá thường? */
    public boolean hasUsableFlashPrice() {
        return this.flashPrice != null && this.flashPrice > 0L;
    }

    /**
     * Dòng này được hưởng giá flash cho {@code quantity} máy không?
     *
     * <p>
     * D32 chọn cách "âm thầm về giá thường" (đồng nhất D27): khách đã dùng hết
     * suất của mình thì dòng rơi về giá gốc, KHÔNG chặn đơn. Một dòng chỉ có một
     * giá nên đòi hỏi cả số lượng đều nằm trong suất còn lại — khách mua 3 máy mà
     * chỉ còn suất 1 thì cả dòng về giá thường, không tách giá nửa nọ nửa kia.
     *
     * <p>
     * {@code perUserLimitLeft} null = chưa xét theo khách (trang danh sách) nên
     * coi như không giới hạn.
     */
    public boolean allowsFlashFor(long quantity) {
        if (!hasUsableFlashPrice()) {
            return false;
        }
        return this.perUserLimitLeft == null || this.perUserLimitLeft >= quantity;
    }
}
