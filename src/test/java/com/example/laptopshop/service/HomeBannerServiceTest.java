package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.BannerTargetType;
import com.example.laptopshop.domain.Category;
import com.example.laptopshop.domain.HomeBanner;
import com.example.laptopshop.dto.request.HomeBanner.HomeBannerCreationRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.FlashSaleRepository;
import com.example.laptopshop.repository.HomeBannerRepository;
import com.example.laptopshop.repository.ProductRepository;

/**
 * Test HomeBannerService — trọng tâm là D30: banner không được thành lỗ hổng
 * open-redirect / XSS qua link admin dán.
 */
@ExtendWith(MockitoExtension.class)
class HomeBannerServiceTest {

    @Mock
    private HomeBannerRepository homeBannerRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FlashSaleRepository flashSaleRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private UploadService uploadService;

    @InjectMocks
    private HomeBannerService homeBannerService;

    @BeforeEach
    void setUp() {
        // lenient: nhiều test cố tình fail ở validate trước khi chạm tới upload.
        lenient().when(uploadService.handleSaveUploadUrl(any(), any())).thenReturn("https://cdn/banner.jpg");
    }

    private HomeBannerCreationRequest request(BannerTargetType type, String value) {
        HomeBannerCreationRequest req = new HomeBannerCreationRequest();
        req.setTitle("Khuyến mại tháng 9");
        req.setTargetType(type);
        req.setTargetValue(value);
        req.setImageUrl("https://cdn/banner.jpg");
        return req;
    }

    private void stubSave() {
        when(homeBannerRepository.save(any(HomeBanner.class))).thenAnswer(inv -> {
            HomeBanner b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId("banner-1");
            }
            return b;
        });
    }

    // ==================================================================
    // D30 — chặn link nguy hiểm
    // ==================================================================

    @Nested
    @DisplayName("D30 — link nội bộ")
    class UrlTarget {

        @Test
        @DisplayName("Đường dẫn nội bộ \"/...\" → hợp lệ")
        void duongDanNoiBo() {
            stubSave();

            var res = homeBannerService.create(request(BannerTargetType.URL, "/laptop-gaming"), null);

            assertEquals("/laptop-gaming", res.getTargetValue());
        }

        @Test
        @DisplayName("javascript: → INVALID_BANNER_TARGET_URL (chặn XSS)")
        void chanJavascript() {
            AppException ex = assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.URL, "javascript:alert(1)"), null));
            assertEquals(ErrorCode.INVALID_BANNER_TARGET_URL, ex.getErrorCode());
        }

        @Test
        @DisplayName("Link tuyệt đối ra ngoài → INVALID_BANNER_TARGET_URL (chặn open-redirect)")
        void chanLinkNgoai() {
            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.URL, "https://evil.example.com"), null));
        }

        @Test
        @DisplayName("Protocol-relative \"//evil.com\" → INVALID_BANNER_TARGET_URL")
        void chanProtocolRelative() {
            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.URL, "//evil.example.com"), null));
        }

        @Test
        @DisplayName("JAVASCRIPT: viết hoa vẫn bị chặn (so khớp không phân biệt hoa thường)")
        void chanJavascriptHoa() {
            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.URL, "JaVaScRiPt:alert(1)"), null));
        }
    }

    // ==================================================================
    // D30 — đối tượng đích phải tồn tại
    // ==================================================================

    @Nested
    @DisplayName("D30 — đối tượng đích")
    class TargetExistence {

        @Test
        @DisplayName("PRODUCT không tồn tại → INVALID_BANNER_TARGET")
        void productKhongTonTai() {
            when(productRepository.findById("missing")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.PRODUCT, "missing"), null));
            assertEquals(ErrorCode.INVALID_BANNER_TARGET, ex.getErrorCode());
        }

        @Test
        @DisplayName("CATEGORY đang tắt → INVALID_BANNER_TARGET (không dẫn khách vào danh mục ẩn)")
        void categoryDangTat() {
            Category category = new Category();
            category.setId("cat-1");
            category.setActive(false);
            when(categoryService.getCategoryById("cat-1")).thenReturn(category);

            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.CATEGORY, "cat-1"), null));
        }

        @Test
        @DisplayName("FLASH_SALE không tồn tại → INVALID_BANNER_TARGET")
        void flashSaleKhongTonTai() {
            when(flashSaleRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.FLASH_SALE, "missing"), null));
        }

        @Test
        @DisplayName("BRAND có sản phẩm → hợp lệ, so khớp không phân biệt hoa thường")
        void brandHopLe() {
            stubSave();
            when(productRepository.findDistinctActiveFactories()).thenReturn(new ArrayList<>(List.of("ASUS")));

            var res = homeBannerService.create(request(BannerTargetType.BRAND, "asus"), null);

            assertEquals(BannerTargetType.BRAND, res.getTargetType());
        }

        @Test
        @DisplayName("BRAND không có sản phẩm nào → INVALID_BANNER_TARGET")
        void brandKhongTonTai() {
            when(productRepository.findDistinctActiveFactories()).thenReturn(new ArrayList<>(List.of("ASUS")));

            assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.BRAND, "Dell"), null));
        }
    }

    // ==================================================================
    // Validate chung + ảnh
    // ==================================================================

    @Nested
    @DisplayName("Validate & ảnh")
    class ValidateAndImage {

        @Test
        @DisplayName("Thiếu tiêu đề → BANNER_TITLE_REQUIRED")
        void thieuTieuDe() {
            HomeBannerCreationRequest req = request(BannerTargetType.URL, "/laptop");
            req.setTitle("  ");

            AppException ex = assertThrows(AppException.class, () -> homeBannerService.create(req, null));
            assertEquals(ErrorCode.BANNER_TITLE_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Thiếu nơi dẫn tới → BANNER_TARGET_REQUIRED")
        void thieuNoiDanToi() {
            AppException ex = assertThrows(AppException.class, () -> homeBannerService
                    .create(request(BannerTargetType.URL, "   "), null));
            assertEquals(ErrorCode.BANNER_TARGET_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Tạo banner không ảnh → BANNER_IMAGE_REQUIRED (slide phải có ảnh)")
        void thieuAnh() {
            HomeBannerCreationRequest req = request(BannerTargetType.URL, "/laptop");
            req.setImageUrl(null);

            AppException ex = assertThrows(AppException.class, () -> homeBannerService.create(req, null));
            assertEquals(ErrorCode.BANNER_IMAGE_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("sortOrder null → 0; active null → true (mặc định của DTO)")
        void macDinhSortOrderVaActive() {
            stubSave();
            HomeBannerCreationRequest req = request(BannerTargetType.URL, "/laptop");

            var res = homeBannerService.create(req, null);

            assertEquals(0, res.getSortOrder());
            assertTrue(res.isActive());
        }

        @Test
        @DisplayName("removeImage khi update → xóa ảnh Cloudinary và set null")
        void removeImageKhiUpdate() {
            HomeBanner banner = new HomeBanner();
            banner.setId("banner-1");
            banner.setImage("https://cdn/old.jpg");
            when(homeBannerRepository.findById("banner-1")).thenReturn(Optional.of(banner));
            when(homeBannerRepository.save(any(HomeBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            HomeBannerCreationRequest req = request(BannerTargetType.URL, "/laptop");
            req.setImageUrl(null);
            req.setRemoveImage(true);

            var res = homeBannerService.update("banner-1", req, null);

            verify(uploadService).handleDeleteFile("https://cdn/old.jpg");
            assertNull(res.getImage());
        }

        @Test
        @DisplayName("Update giữ ảnh cũ khi không gửi file/url và không removeImage")
        void updateGiuAnhCu() {
            HomeBanner banner = new HomeBanner();
            banner.setId("banner-1");
            banner.setImage("https://cdn/old.jpg");
            when(homeBannerRepository.findById("banner-1")).thenReturn(Optional.of(banner));
            when(homeBannerRepository.save(any(HomeBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            HomeBannerCreationRequest req = request(BannerTargetType.URL, "/laptop");
            req.setImageUrl(null);

            var res = homeBannerService.update("banner-1", req, null);

            assertEquals("https://cdn/old.jpg", res.getImage());
            verify(uploadService, never()).handleDeleteFile(any());
        }
    }

    // ==================================================================
    // Đọc / xóa
    // ==================================================================

    @Nested
    @DisplayName("Đọc & xóa")
    class ReadAndDelete {

        @Test
        @DisplayName("getActiveBanners chỉ trả banner đang bật")
        void chiTraBannerDangBat() {
            HomeBanner on = new HomeBanner();
            on.setId("b1");
            on.setActive(true);
            when(homeBannerRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(on));

            var res = homeBannerService.getActiveBanners();

            assertEquals(1, res.size());
            assertTrue(res.get(0).isActive());
        }

        @Test
        @DisplayName("Xóa banner có ảnh → xóa ảnh Cloudinary rồi xóa mềm")
        void xoaKemAnh() {
            HomeBanner banner = new HomeBanner();
            banner.setId("banner-1");
            banner.setImage("https://cdn/banner.jpg");
            when(homeBannerRepository.findById("banner-1")).thenReturn(Optional.of(banner));

            homeBannerService.deleteBanner("banner-1");

            verify(uploadService).handleDeleteFile("https://cdn/banner.jpg");
            verify(homeBannerRepository).delete(banner);
        }

        @Test
        @DisplayName("Không tìm thấy → BANNER_NOT_FOUND")
        void khongTimThay() {
            when(homeBannerRepository.findById("missing")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> homeBannerService.getBannerById("missing"));
            assertEquals(ErrorCode.BANNER_NOT_FOUND, ex.getErrorCode());
        }
    }
}
