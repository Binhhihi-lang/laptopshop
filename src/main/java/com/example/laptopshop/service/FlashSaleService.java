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
import org.springframework.web.multipart.MultipartFile;

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
    UploadService uploadService;

    // ===== Nguồn giá (đọc) =====

    /**
     * Giá flash hiện hành cho trang danh sách/thẻ (không có user → không tính
     * giới hạn/người; {@code perUserLimitLeft} null nghĩa là "chưa xét").
     * Sản phẩm hết phiên → vắng khỏi map, chỗ gọi fallback giá thường (D27).
     */
    @Transactional(readOnly = true)
    public Map<String, FlashPriceView> resolvePriceMap(List<String> productIds, LocalDateTime now) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinct = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }

        List<FlashSaleItem> items = this.flashSaleItemRepository.findCurrentByProductIds(distinct, now);
        Map<String, FlashPriceView> result = new LinkedHashMap<>();
        for (FlashSaleItem item : items) {
            if (item.getProduct() == null || !item.hasFlashStock()) {
                continue; // kho phiên cạn rồi → dòng đó về giá thường (D27)
            }
            result.putIfAbsent(item.getProduct().getId(), toPriceView(item));
        }
        return result;
    }

    /** Bản cho giỏ/chốt đơn: thêm {@code perUserLimitLeft} đúng theo khách (D32). */
    @Transactional(readOnly = true)
    public Map<String, FlashPriceView> resolvePriceMap(List<String> productIds, String userId,
            LocalDateTime now) {
        Map<String, FlashPriceView> base = resolvePriceMap(productIds, now);
        if (userId == null || userId.isBlank() || base.isEmpty()) {
            return base;
        }
        Map<String, FlashPriceView> withLimit = new HashMap<>(base.size());
        for (Map.Entry<String, FlashPriceView> entry : base.entrySet()) {
            withLimit.put(entry.getKey(), applyPerUserLimit(entry.getValue(), userId, now));
        }
        return withLimit;
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
        return this.flashSaleRepository.findAll().stream().map(this::toResponse).toList();
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
    public FlashSaleResponse create(FlashSaleCreationRequest request, MultipartFile file) {
        LocalDateTime now = LocalDateTime.now();
        validate(request, now);

        FlashSale flashSale = new FlashSale();
        applyFields(flashSale, request);
        applyBannerImage(flashSale, file, request);
        applyItems(flashSale, request.getItems());

        FlashSale saved = this.flashSaleRepository.save(flashSale);
        return toResponse(saved);
    }

    /**
     * Cập nhật phiên + dựng lại item. Item nào trùng sản phẩm thì GIỮ object cũ
     * để không mất {@code soldInFlash}, chỉ đổi giá/kho/limit.
     */
    @Transactional
    public FlashSaleResponse update(String id, FlashSaleCreationRequest request, MultipartFile file) {
        FlashSale flashSale = findOrThrow(id);
        validate(request, LocalDateTime.now());
        applyFields(flashSale, request);
        applyBannerImage(flashSale, file, request);

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
                    // Giữ object cũ để nguyên soldInFlash — chỉ đổi giá/kho/limit.
                    validateItemFields(itemRequest);
                    existing.setFlashPrice(itemRequest.getFlashPrice());
                    existing.setFlashStock(itemRequest.getFlashStock());
                    existing.setPerUserLimit(itemRequest.getPerUserLimit());
                    validateFlashPriceBelowSellingPrice(itemRequest.getFlashPrice(), existing.getProduct());
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
        if (flashSale.getBannerImage() != null) {
            this.uploadService.handleDeleteFile(flashSale.getBannerImage());
        }
        this.flashSaleRepository.delete(flashSale);
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
        if (itemRequest.getFlashStock() == null || itemRequest.getFlashStock() <= 0) {
            throw new AppException(ErrorCode.INVALID_FLASH_STOCK);
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

    private void applyFields(FlashSale flashSale, FlashSaleCreationRequest request) {
        flashSale.setName(request.getName().trim());
        flashSale.setDescription(request.getDescription());
        flashSale.setStartAt(request.getStartAt());
        flashSale.setEndAt(request.getEndAt());
        flashSale.setActive(request.getActive() == null || request.getActive());
    }

    private void applyBannerImage(FlashSale flashSale, MultipartFile file, FlashSaleCreationRequest request) {
        boolean hasNewFile = file != null && !file.isEmpty();
        boolean hasImageUrl = request.getImageUrl() != null && !request.getImageUrl().isBlank();
        if (!hasNewFile && !hasImageUrl) {
            return;
        }
        if (flashSale.getBannerImage() != null) {
            this.uploadService.handleDeleteFile(flashSale.getBannerImage());
        }
        flashSale.setBannerImage(hasNewFile
                ? this.uploadService.handleSaveUploadFile(file, "flash-sale")
                : this.uploadService.handleSaveUploadUrl(request.getImageUrl(), "flash-sale"));
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
     * Gắn lại {@code perUserLimitLeft} theo đúng khách (D32). Khác với suất cả
     * phiên: khách A mua 1 máy không làm mất suất của khách B.
     */
    private FlashPriceView applyPerUserLimit(FlashPriceView view, String userId, LocalDateTime now) {
        if (view.itemId() == null || view.flashSaleId() == null) {
            return view;
        }
        FlashSaleItem item = this.flashSaleItemRepository.findById(view.itemId()).orElse(null);
        if (item == null || item.getPerUserLimit() == null || item.getProduct() == null) {
            return view; // null = phiên không giới hạn -> giữ nguyên view
        }
        FlashSale sale = this.flashSaleRepository.findById(view.flashSaleId()).orElse(null);
        if (sale == null) {
            return view;
        }
        long bought = this.flashSaleItemRepository.countQtyBoughtByUserInWindow(
                userId, item.getProduct().getId(), sale.getStartAt(), sale.getEndAt());
        int left = (int) Math.max(0, item.getPerUserLimit() - bought);
        return new FlashPriceView(view.itemId(), view.flashSaleId(), view.flashPrice(),
                view.flashStock(), view.soldInFlash(), view.endAt(), left);
    }

    private FlashPriceView toPriceView(FlashSaleItem item) {
        return new FlashPriceView(
                item.getId(),
                item.getFlashSale() == null ? null : item.getFlashSale().getId(),
                item.getFlashPrice(),
                item.getFlashStock(),
                item.getSoldInFlash(),
                item.getFlashSale() == null ? null : item.getFlashSale().getEndAt(),
                null); // perUserLimitLeft: chưa xét — xem note trên resolvePriceMap
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
                .bannerImage(flashSale.getBannerImage())
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
