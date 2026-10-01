package com.example.laptopshop.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.BannerTargetType;
import com.example.laptopshop.domain.Category;
import com.example.laptopshop.domain.FlashSale;
import com.example.laptopshop.domain.HomeBanner;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.HomeBanner.HomeBannerCreationRequest;
import com.example.laptopshop.dto.response.HomeBanner.HomeBannerResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.FlashSaleRepository;
import com.example.laptopshop.repository.HomeBannerRepository;
import com.example.laptopshop.repository.ProductRepository;

/** CRUD slide carousel trang chủ — khối hiển thị, không đụng logic tiền (§0.6). */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HomeBannerService {

    /** Trần số slide bật cùng lúc — khớp số chấm của carousel trang chủ. */
    private static final int MAX_ACTIVE_BANNERS = 5;

    HomeBannerRepository homeBannerRepository;
    ProductRepository productRepository;
    FlashSaleRepository flashSaleRepository;
    CategoryService categoryService;
    UploadService uploadService;

    /** Slide khách thấy ở trang chủ: chỉ banner bật, theo sortOrder. */
    @Transactional(readOnly = true)
    public List<HomeBannerResponse> getActiveBanners() {
        return this.homeBannerRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    /** Toàn bộ banner (gồm cả đang tắt) cho trang quản trị. */
    @Transactional(readOnly = true)
    public List<HomeBannerResponse> getAllBanners() {
        return this.homeBannerRepository.findAllByOrderBySortOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HomeBannerResponse getBannerById(String id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public HomeBannerResponse create(HomeBannerCreationRequest request, MultipartFile file) {
        validate(request);
        ensureActiveLimit(null, request.getActive() == null || request.getActive());
        HomeBanner banner = new HomeBanner();
        applyFields(banner, request);
        applyImage(banner, request, file, false);
        return toResponse(this.homeBannerRepository.save(banner));
    }

    @Transactional
    public HomeBannerResponse update(String id, HomeBannerCreationRequest request, MultipartFile file) {
        HomeBanner banner = findOrThrow(id);
        validate(request);
        ensureActiveLimit(id, request.getActive() == null || request.getActive());
        applyFields(banner, request);
        applyImage(banner, request, file, true);
        return toResponse(this.homeBannerRepository.save(banner));
    }

    /** Bật/tắt slide ngay trên thẻ ở màn danh sách. */
    @Transactional
    public HomeBannerResponse setActive(String id, boolean active) {
        HomeBanner banner = findOrThrow(id);
        ensureActiveLimit(id, active);
        banner.setActive(active);
        return toResponse(this.homeBannerRepository.save(banner));
    }

    /**
     * Trần 5 slide đang bật — carousel trang chủ chỉ hiển thị gọn trong 5 chấm.
     * {@code excludeId} là chính slide đang sửa, không tính vào số đang bật.
     */
    private void ensureActiveLimit(String excludeId, boolean willBeActive) {
        if (!willBeActive) {
            return;
        }
        long activeCount = excludeId == null
                ? this.homeBannerRepository.countByActiveTrue()
                : this.homeBannerRepository.countByActiveTrueAndIdNot(excludeId);
        if (activeCount >= MAX_ACTIVE_BANNERS) {
            throw new AppException(ErrorCode.BANNER_ACTIVE_LIMIT_EXCEEDED);
        }
    }

    /** Xóa mềm banner + xóa ảnh Cloudinary. */
    @Transactional
    public void deleteBanner(String id) {
        HomeBanner banner = findOrThrow(id);
        if (banner.getImage() != null) {
            this.uploadService.handleDeleteFile(banner.getImage());
        }
        this.homeBannerRepository.delete(banner);
    }

    /**
     * Kiểm tra đối tượng đích có thật và đang dùng được. Không còn loại URL tự
     * do nên mọi đích đều tra được trong DB — không có đường cho link ngoài.
     */
    private void validate(HomeBannerCreationRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new AppException(ErrorCode.BANNER_TITLE_REQUIRED);
        }
        if (request.getTargetType() == null) {
            throw new AppException(ErrorCode.BANNER_TARGET_REQUIRED);
        }
        String value = request.getTargetValue() == null ? null : request.getTargetValue().trim();
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.BANNER_TARGET_REQUIRED);
        }
        validateTarget(request.getTargetType(), value);
    }

    private void validateTarget(BannerTargetType type, String value) {
        switch (type) {
            // Lưu CODE sản phẩm (không phải id): route khách là /products/:code.
            case PRODUCT -> productRepository.findByCodeIgnoreCase(value)
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_BANNER_TARGET));
            case CATEGORY -> {
                Category category = categoryService.getCategoryById(value);
                if (!category.isActive()) {
                    throw new AppException(ErrorCode.INVALID_BANNER_TARGET);
                }
            }
            case FLASH_SALE -> flashSaleRepository.findById(value)
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_BANNER_TARGET));
            case BRAND -> {
                // factory không có bảng riêng — chỉ kiểm tra có sản phẩm nào mang hãng này.
                if (productRepository.findDistinctActiveFactories().stream()
                        .noneMatch(f -> f.equalsIgnoreCase(value))) {
                    throw new AppException(ErrorCode.INVALID_BANNER_TARGET);
                }
            }
        }
    }

    private void applyFields(HomeBanner banner, HomeBannerCreationRequest request) {
        banner.setTitle(request.getTitle().trim());
        banner.setKicker(request.getKicker() == null ? null : request.getKicker().trim());
        banner.setSubtitle(request.getSubtitle());
        banner.setTargetType(request.getTargetType());
        banner.setTargetValue(request.getTargetValue().trim());
        banner.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        banner.setActive(request.getActive() == null || request.getActive());
    }

    private void applyImage(HomeBanner banner, HomeBannerCreationRequest request, MultipartFile file,
            boolean isUpdate) {
        boolean hasNewFile = file != null && !file.isEmpty();
        boolean hasImageUrl = request.getImageUrl() != null && !request.getImageUrl().isBlank();
        if (hasNewFile || hasImageUrl) {
            if (isUpdate && banner.getImage() != null) {
                this.uploadService.handleDeleteFile(banner.getImage());
            }
            banner.setImage(hasNewFile
                    ? this.uploadService.handleSaveUploadFile(file, "banner")
                    : this.uploadService.handleSaveUploadUrl(request.getImageUrl(), "banner"));
        } else if (isUpdate && request.isRemoveImage() && banner.getImage() != null) {
            this.uploadService.handleDeleteFile(banner.getImage());
            banner.setImage(null);
        } else if (banner.getImage() == null) {
            // Ảnh bắt buộc cho slide; create thiếu thì phải có file/url.
            throw new AppException(ErrorCode.BANNER_IMAGE_REQUIRED);
        }
    }

    private HomeBanner findOrThrow(String id) {
        return this.homeBannerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BANNER_NOT_FOUND));
    }

    private HomeBannerResponse toResponse(HomeBanner banner) {
        return HomeBannerResponse.builder()
                .id(banner.getId())
                .title(banner.getTitle())
                .kicker(banner.getKicker())
                .subtitle(banner.getSubtitle())
                .image(banner.getImage())
                .targetType(banner.getTargetType())
                .targetValue(banner.getTargetValue())
                .sortOrder(banner.getSortOrder())
                .active(banner.isActive())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
