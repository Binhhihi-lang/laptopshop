package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.FlashSale;
import com.example.laptopshop.domain.FlashSaleItem;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.FlashSale.FlashSaleCreationRequest;
import com.example.laptopshop.dto.request.FlashSale.FlashSaleItemRequest;
import com.example.laptopshop.dto.response.Client.FlashPriceView;
import com.example.laptopshop.dto.response.FlashSale.FlashSaleResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.FlashSaleItemRepository;
import com.example.laptopshop.repository.FlashSaleRepository;
import com.example.laptopshop.repository.ProductRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * CRUD phiên flash sale + nguồn giá flash duy nhất cho card/giỏ/chốt đơn qua
 * {@link #resolvePriceMap} (§3.1b). Engine không biết flash sale — chỉ nhận
 * {@code Line.flashPrice} đã điền sẵn (D25).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FlashSaleService {

    FlashSaleRepository flashSaleRepository;
    FlashSaleItemRepository flashSaleItemRepository;
    ProductRepository productRepository;

    // ===== Nguồn giá (đọc) =====

    /**
     * Giá flash hiện hành cho trang danh sách/thẻ (không có user → không tính
     * giới hạn/người; {@code perUserLimitLeft} null nghĩa là "chưa xét").
     * Sản phẩm hết phiên → vắng khỏi map, chỗ gọi fallback giá thường (D27).
     */
    @Transactional(readOnly = true)
    public Map<String, FlashPriceView> resolvePriceMap(List<String> productIds, LocalDateTime now) {
        return toViewMap(findCurrentItems(productIds, now), Map.of(), false);
    }

    /** Bản cho giỏ/chốt đơn: thêm {@code perUserLimitLeft} đúng theo khách (D32). */
    @Transactional(readOnly = true)
    public Map<String, FlashPriceView> resolvePriceMap(List<String> productIds, String userId,
            LocalDateTime now) {
        List<FlashSaleItem> items = findCurrentItems(productIds, now);
        if (items.isEmpty() || userId == null || userId.isBlank()) {
            return toViewMap(items, Map.of(), false);
        }
        return toViewMap(items, sumBoughtByItem(items, userId), true);
    }

    /**
     * Các item flash còn hiệu lực của nhóm sản phẩm — 1 query {@code IN} duy nhất
     * (BR-F06), đã {@code JOIN FETCH} sẵn product + flashSale.
     */
    private List<FlashSaleItem> findCurrentItems(List<String> productIds, LocalDateTime now) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<String> distinct = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return this.flashSaleItemRepository.findCurrentByProductIds(distinct, now);
    }

    /**
     * Số máy khách đã mua cho từng item flash (BR-F14), gộp theo
     * {@code flashSaleItemId} mà đơn đã lưu lúc chốt.
     *
     * <p>
     * Khớp ĐÚNG item nên hai phiên trùng khung giờ không đếm lẫn nhau (trước đây
     * lọc theo {@code orderDate BETWEEN startAt AND endAt} nên đếm chéo). Gom theo
     * PHIÊN rồi hỏi 1 câu cho cả phiên thay vì 1 câu cho từng item (tránh N+1).
     *
     * <p>
     * Chỉ hỏi những phiên THỰC SỰ có ít nhất một item đặt {@code perUserLimit} —
     * phiên không giới hạn thì không cần biết khách đã mua bao nhiêu.
     *
     * @return flashSaleItemId → tổng số đã mua; item chưa mua không có mặt (coi = 0)
     */
    private Map<String, Long> sumBoughtByItem(List<FlashSaleItem> items, String userId) {
        Map<String, List<FlashSaleItem>> bySale = new LinkedHashMap<>();
        for (FlashSaleItem item : items) {
            if (item.getId() == null || item.getProduct() == null || item.getFlashSale() == null
                    || item.getPerUserLimit() == null) {
                continue;
            }
            bySale.computeIfAbsent(item.getFlashSale().getId(), k -> new ArrayList<>()).add(item);
        }

        Map<String, Long> bought = new HashMap<>();
        for (List<FlashSaleItem> saleItems : bySale.values()) {
            List<String> itemIds = saleItems.stream()
                    .map(FlashSaleItem::getId)
                    .distinct()
                    .toList();
            for (Object[] row : this.flashSaleItemRepository.sumQtyBoughtByUserForItems(userId, itemIds)) {
                bought.put((String) row[0], ((Number) row[1]).longValue());
            }
        }
        return bought;
    }

    /**
     * Dựng map kết quả từ danh sách item. Item nào kho phiên cạn thì bỏ — chỗ gọi
     * fallback giá thường (D27).
     *
     * @param boughtByProduct productId → số đã mua; rỗng = khách chưa mua gì
     * @param perUserAware    true khi gọi cho MỘT khách cụ thể (giỏ/chốt đơn): lúc
     *                        đó {@code perUserLimitLeft} phải có giá trị kể cả khi
     *                        khách chưa mua gì (0 đã mua → left = trần). Trước đây
     *                        chỉ tính khi đã có người mua nên khách mới bị coi là
     *                        "không giới hạn" → mua vượt trần mà không ai chặn.
     */
    private Map<String, FlashPriceView> toViewMap(List<FlashSaleItem> items, Map<String, Long> boughtByProduct,
            boolean perUserAware) {
        Map<String, FlashPriceView> result = new LinkedHashMap<>();
        for (FlashSaleItem item : items) {
            if (item.getProduct() == null || !item.hasFlashStock()) {
                continue; // kho phiên cạn rồi → dòng đó về giá thường (D27)
            }
            result.putIfAbsent(item.getProduct().getId(), toPriceView(item, boughtByProduct, perUserAware));
        }
        return result;
    }

    /** Phiên đang chạy (kết thúc sớm nhất trước) — trang /flash-sale. */
    @Transactional(readOnly = true)
    public FlashSaleResponse findActiveResponse(LocalDateTime now) {
        return this.flashSaleRepository.findActiveBetween(now).stream()
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    /** Danh sách phiên cho trang quản trị. */
    @Transactional(readOnly = true)
    public List<FlashSaleResponse> getAllResponses() {
        return this.flashSaleRepository.findAllByOrderByStartAtAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FlashSaleResponse getResponseById(String id) {
        return toResponse(findOrThrow(id));
    }

    /** Phiên sắp tới (D31) — FE vẽ "Diễn ra lúc 19:00". */
    @Transactional(readOnly = true)
    public List<FlashSaleResponse> findUpcomingResponses(LocalDateTime now, int limit) {
        return this.flashSaleRepository.findUpcoming(now).stream()
                .limit(Math.max(1, limit))
                .map(this::toResponse).toList();
    }

    // ===== CRUD admin =====

    @Transactional
    public FlashSaleResponse create(FlashSaleCreationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        validate(request, now);
        validateNoOverlap(null, request, now);

        FlashSale flashSale = new FlashSale();
        applyFields(flashSale, request);
        applyItems(flashSale, request.getItems());

        FlashSale saved = this.flashSaleRepository.save(flashSale);
        return toResponse(saved);
    }

    /**
     * Cập nhật phiên + dựng lại item. Item nào trùng sản phẩm thì GIỮ object cũ
     * để không mất {@code soldInFlash} (số đã bán), chỉ đổi giá/kho/limit.
     * {@code flashStock} gửi lên là số CÒN LẠI nên set thẳng — không cộng trừ.
     */
    @Transactional
    public FlashSaleResponse update(String id, FlashSaleCreationRequest request) {
        FlashSale flashSale = findOrThrow(id);
        LocalDateTime now = LocalDateTime.now();
        validate(request, now);
        validateNoOverlap(id, request, now);
        applyFields(flashSale, request);

        Map<String, FlashSaleItem> existingByProduct = new HashMap<>();
        for (FlashSaleItem item : new ArrayList<>(flashSale.getItems())) {
            if (item.getProduct() != null) {
                existingByProduct.put(item.getProduct().getId(), item);
            }
        }

        flashSale.getItems().clear();
        if (request.getItems() != null) {
            for (FlashSaleItemRequest itemRequest : request.getItems()) {
                FlashSaleItem existing = itemRequest.getProductId() == null ? null
                        : existingByProduct.remove(itemRequest.getProductId());
                if (existing != null) {
                    // Giữ object cũ để nguyên soldInFlash (đã bán) — chỉ đổi giá/kho/limit.
                    validateItemFields(itemRequest);
                    existing.setFlashPrice(itemRequest.getFlashPrice());
                    existing.setFlashStock(itemRequest.getFlashStock());
                    existing.setPerUserLimit(itemRequest.getPerUserLimit());
                    validateFlashPriceBelowSellingPrice(itemRequest.getFlashPrice(), existing.getProduct());
                    validateStockWithinProduct(itemRequest.getFlashStock(), existing.getProduct());
                    flashSale.getItems().add(existing);
                } else {
                    flashSale.getItems().add(buildItem(flashSale, itemRequest));
                }
            }
        }
        if (flashSale.getItems().isEmpty()) {
            throw new AppException(ErrorCode.FLASH_SALE_NO_ITEMS);
        }
        return toResponse(this.flashSaleRepository.save(flashSale));
    }

    /**
     * Gỡ phiên. Phải set {@code active = false} RỒI mới xóa mềm: resolvePriceMap
     * không lọc được {@code deleted_at} của bảng cha qua join, {@code active}
     * mới là điều kiện chặn thật.
     */
    @Transactional
    public void deleteFlashSale(String id) {
        FlashSale flashSale = findOrThrow(id);
        flashSale.setActive(false);
        this.flashSaleRepository.save(flashSale);
        this.flashSaleRepository.delete(flashSale);
    }

    /**
     * Bật/tắt công tắc phiên (màn chi tiết: "Tạm dừng" / "Mở lại"). Tắt công tắc
     * là tắt NGAY cả khi còn trong khung giờ — {@code resolvePriceMap} lọc theo
     * {@code active} nên giá flash biến mất khỏi card, KHÔNG chạm giá gốc (D28).
     */
    @Transactional
    public FlashSaleResponse setActive(String id, boolean active) {
        FlashSale flashSale = findOrThrow(id);
        flashSale.setActive(active);
        return toResponse(this.flashSaleRepository.save(flashSale));
    }

    // ===== Trừ / hoàn kho phiên =====

    /**
     * Trừ kho phiên atomic (D29). 0 dòng = cạn giữa lúc chốt → chỗ gọi tính lại
     * dòng đó giá thường, KHÔNG fail đơn (D27).
     */
    @Transactional
    public boolean consumeStock(String itemId, long qty) {
        return this.flashSaleItemRepository.consumeStock(itemId, qty) == 1;
    }

    /** Hoàn kho phiên khi hủy đơn (nối D12). */
    @Transactional
    public void releaseStock(String itemId, long qty) {
        this.flashSaleItemRepository.releaseStock(itemId, qty);
    }

    // ===== Validate & helpers =====

    private FlashSale findOrThrow(String id) {
        return this.flashSaleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_NOT_FOUND));
    }

    private void validate(FlashSaleCreationRequest request, LocalDateTime now) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ErrorCode.FLASH_SALE_NAME_REQUIRED);
        }
        if (request.getStartAt() == null || request.getEndAt() == null
                || !request.getEndAt().isAfter(request.getStartAt())) {
            throw new AppException(ErrorCode.INVALID_FLASH_SALE_DATE_RANGE);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.FLASH_SALE_NO_ITEMS);
        }
        // Chặn trước khi đụng DB: báo lỗi nghiệp vụ rõ hơn message của unique index.
        long distinctProducts = request.getItems().stream()
                .map(FlashSaleItemRequest::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
        if (distinctProducts != request.getItems().size()) {
            throw new AppException(ErrorCode.FLASH_SALE_ITEM_DUPLICATE);
        }
    }

    private void validateItemFields(FlashSaleItemRequest itemRequest) {
        if (itemRequest.getFlashPrice() == null || itemRequest.getFlashPrice() <= 0L) {
            throw new AppException(ErrorCode.INVALID_FLASH_PRICE);
        }
        // flashStock là số CÒN LẠI: 0 = hết suất (hợp lệ, dòng về giá thường), âm = sai.
        if (itemRequest.getFlashStock() == null || itemRequest.getFlashStock() < 0) {
            throw new AppException(ErrorCode.INVALID_FLASH_STOCK);
        }
        // Trần mỗi khách BẮT BUỘC: để trống = vô hạn → một khách ôm hết suất giá
        // sốc, khách khác không mua được (V16). Phải là số nguyên ≥ 1.
        if (itemRequest.getPerUserLimit() == null || itemRequest.getPerUserLimit() < 1) {
            throw new AppException(ErrorCode.FLASH_PER_USER_LIMIT_REQUIRED);
        }
        // Trần không được vượt suất còn lại: suất 3 mà cho 1 khách mua tới 5 thì
        // con số vượt là ảo (cả phiên chỉ có 3 máy).
        if (itemRequest.getPerUserLimit() > itemRequest.getFlashStock()) {
            throw new AppException(ErrorCode.FLASH_PER_USER_LIMIT_EXCEEDS_STOCK);
        }
    }

    /** D26 — flash sale mà không rẻ hơn giá thường thì không phải flash sale. */
    private void validateFlashPriceBelowSellingPrice(Long flashPrice, Product product) {
        if (product == null || flashPrice == null) {
            return;
        }
        if (flashPrice >= product.getPrice()) {
            throw new AppException(ErrorCode.FLASH_PRICE_NOT_LOWER);
        }
    }

    /**
     * Kho phiên = số CÒN LẠI, không được vượt tồn kho thật — nếu vượt, phiên
     * quảng cáo còn suất nhưng lúc chốt {@code deductStock} fail → khách không mua
     * được. Message nêu TÊN sản phẩm và số vượt để admin biết sửa dòng nào.
     */
    private void validateStockWithinProduct(Integer remaining, Product product) {
        if (product == null || remaining == null) {
            return;
        }
        if (remaining > product.getQuantity()) {
            throw new AppException(ErrorCode.FLASH_STOCK_EXCEEDS_PRODUCT_STOCK,
                    String.format("Kho phiên của \"%s\" là %d, vượt quá tồn kho hiện tại (%d) — vui lòng giảm %d",
                            product.getName(), remaining, product.getQuantity(),
                            remaining - product.getQuantity()));
        }
    }

    /**
     * TASK-001 — chặn phiên trùng: cùng sản phẩm + khung giờ giao nhau với phiên
     * CHƯA kết thúc. Giữ cách xử lý hiển thị cũ (kết thúc sớm nhất thắng), chỉ
     * ngăn admin tạo ra tình huống mơ hồ ngay từ đầu.
     *
     * @param excludeId id phiên đang sửa (bỏ qua chính nó); null khi tạo mới
     */
    private void validateNoOverlap(String excludeId, FlashSaleCreationRequest request, LocalDateTime now) {
        if (request.getItems() == null || request.getItems().isEmpty()
                || request.getStartAt() == null || request.getEndAt() == null) {
            return;
        }
        List<String> productIds = request.getItems().stream()
                .map(FlashSaleItemRequest::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return;
        }
        List<FlashSale> overlapping = this.flashSaleRepository.findOverlapping(
                excludeId == null ? "" : excludeId,
                request.getStartAt(), request.getEndAt(), now, productIds);
        if (!overlapping.isEmpty()) {
            FlashSale other = overlapping.get(0);
            throw new AppException(ErrorCode.FLASH_SALE_OVERLAP,
                    String.format("Khung giờ trùng với phiên \"%s\" (%s – %s) cùng sản phẩm — hãy đổi thời gian hoặc sản phẩm",
                            other.getName(),
                            other.getStartAt(), other.getEndAt()));
        }
    }

    private void applyFields(FlashSale flashSale, FlashSaleCreationRequest request) {
        flashSale.setName(request.getName().trim());
        flashSale.setDescription(request.getDescription());
        flashSale.setStartAt(request.getStartAt());
        flashSale.setEndAt(request.getEndAt());
        flashSale.setActive(request.getActive() == null || request.getActive());
    }

    private void applyItems(FlashSale flashSale, List<FlashSaleItemRequest> items) {
        for (FlashSaleItemRequest itemRequest : items) {
            flashSale.getItems().add(buildItem(flashSale, itemRequest));
        }
    }

    private FlashSaleItem buildItem(FlashSale flashSale, FlashSaleItemRequest itemRequest) {
        validateItemFields(itemRequest);
        if (itemRequest.getProductId() == null || itemRequest.getProductId().isBlank()) {
            throw new AppException(ErrorCode.FLASH_SALE_PRODUCT_NOT_FOUND);
        }
        Product product = this.productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_PRODUCT_NOT_FOUND));
        validateFlashPriceBelowSellingPrice(itemRequest.getFlashPrice(), product);
        validateStockWithinProduct(itemRequest.getFlashStock(), product);

        FlashSaleItem item = new FlashSaleItem();
        item.setFlashSale(flashSale);
        item.setProduct(product);
        item.setFlashPrice(itemRequest.getFlashPrice());
        item.setFlashStock(itemRequest.getFlashStock());
        item.setPerUserLimit(itemRequest.getPerUserLimit());
        item.setSoldInFlash(0);
        return item;
    }

    /**
     * Dựng view cho một item. {@code perUserLimitLeft} chỉ có giá trị khi
     * {@code perUserAware} = true (bản dành cho giỏ/chốt đơn); bản cho danh sách
     * sản phẩm để null nghĩa "chưa xét giới hạn/khách".
     */
    private FlashPriceView toPriceView(FlashSaleItem item, Map<String, Long> boughtByItem, boolean perUserAware) {
        Integer perUserLimitLeft = null;
        Integer perUserLimit = item.getPerUserLimit();
        if (perUserAware && perUserLimit != null) {
            long bought = boughtByItem.getOrDefault(item.getId(), 0L);
            perUserLimitLeft = (int) Math.max(0, perUserLimit - bought);
        }
        return new FlashPriceView(
                item.getId(),
                item.getFlashSale() == null ? null : item.getFlashSale().getId(),
                item.getFlashPrice(),
                item.getFlashStock(),
                item.getSoldInFlash(),
                item.getFlashSale() == null ? null : item.getFlashSale().getEndAt(),
                item.getPerUserLimit(),
                perUserLimitLeft);
    }

    // ===== Response =====

    private FlashSaleResponse toResponse(FlashSale flashSale) {
        List<com.example.laptopshop.dto.response.FlashSale.FlashSaleItemResponse> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (FlashSaleItem item : flashSale.getItems()) {
            Product product = item.getProduct();
            items.add(com.example.laptopshop.dto.response.FlashSale.FlashSaleItemResponse.builder()
                    .id(item.getId())
                    .productId(product == null ? null : product.getId())
                    .productCode(product == null ? null : product.getCode())
                    .productName(product == null ? null : product.getName())
                    .productImage(product == null ? null : product.getImage())
                    .flashPrice(item.getFlashPrice())
                    .flashStock(item.getFlashStock())
                    .soldInFlash(item.getSoldInFlash())
                    .remainingStock(item.getRemainingFlashStock())
                    .perUserLimit(item.getPerUserLimit())
                    .regularPrice(product == null ? null : product.getPrice())
                    .build());
        }
        return FlashSaleResponse.builder()
                .id(flashSale.getId())
                .name(flashSale.getName())
                .description(flashSale.getDescription())
                .startAt(flashSale.getStartAt())
                .endAt(flashSale.getEndAt())
                .active(flashSale.isActive())
                .running(flashSale.isRunning(now))
                .itemCount(items.size())
                .items(items)
                .createdAt(flashSale.getCreatedAt())
                .updatedAt(flashSale.getUpdatedAt())
                .build();
    }
}
