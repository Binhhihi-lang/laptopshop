package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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

import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionExclude;
import com.example.laptopshop.domain.PromotionScope;
import com.example.laptopshop.domain.PromotionType;
import com.example.laptopshop.domain.ScopeType;
import com.example.laptopshop.dto.request.Promotion.PromotionCreationRequest;
import com.example.laptopshop.dto.request.Promotion.PromotionUpdateRequest;
import com.example.laptopshop.dto.response.Promotion.PromotionResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.repository.PromotionRepository;
import com.example.laptopshop.repository.PromotionScopeRepository;

/**
 * Test cho PromotionService — CRUD admin + quy tắc validate.
 */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private PromotionService promotionService;

    private PromotionCreationRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = new PromotionCreationRequest();
        baseRequest.setName("Sale tháng 9");
        baseRequest.setTitle("Giảm 10%");
        baseRequest.setType(PromotionType.PRODUCT_DISCOUNT);
        baseRequest.setDiscountType(PromotionDiscountType.PERCENT);
        baseRequest.setDiscountValue(10L);
        baseRequest.setStartDate(LocalDateTime.now().minusDays(1));
        baseRequest.setEndDate(LocalDateTime.now().plusDays(10));
        baseRequest.setScopeType(ScopeType.ALL);
        baseRequest.setScopeValues(new ArrayList<>(List.of("ignored")));
    }

    /** Bản sao request cho luồng update (cùng shape, khác kiểu DTO). */
    private PromotionUpdateRequest updateRequest() {
        PromotionUpdateRequest req = new PromotionUpdateRequest();
        req.setName(baseRequest.getName());
        req.setTitle(baseRequest.getTitle());
        req.setType(baseRequest.getType());
        req.setDiscountType(baseRequest.getDiscountType());
        req.setDiscountValue(baseRequest.getDiscountValue());
        req.setStartDate(baseRequest.getStartDate());
        req.setEndDate(baseRequest.getEndDate());
        req.setScopeType(baseRequest.getScopeType());
        req.setScopeValues(new ArrayList<>(baseRequest.getScopeValues()));
        req.setExcludeProductIds(new ArrayList<>(baseRequest.getExcludeProductIds()));
        return req;
    }

    private void stubSave() {
        lenient().when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("promo-1");
            }
            return p;
        });
    }

    @Nested
    @DisplayName("Tạo chương trình")
    class Create {

        @Test
        @DisplayName("Tạo hợp lệ → lưu và trả response")
        void taoHopLe() {
            stubSave();

            PromotionResponse res = promotionService.create(baseRequest);

            assertNotNull(res);
            assertEquals("Sale tháng 9", res.getName());
            assertEquals(PromotionDiscountType.PERCENT, res.getDiscountType());
            assertEquals(10L, res.getDiscountValue());
            verify(promotionRepository).save(any(Promotion.class));
        }

        @Test
        @DisplayName("Thiếu tên → PROMOTION_NAME_REQUIRED")
        void thieuTen() {
            baseRequest.setName("   ");

            AppException ex = assertThrows(AppException.class,
                    () -> promotionService.create(baseRequest));
            assertEquals("Tên chương trình khuyến mại không được để trống",
                    ex.getErrorCode().getMessage());
        }

        @Test
        @DisplayName("endDate trước startDate → INVALID_PROMOTION_DATE_RANGE")
        void khoangThoiGianSai() {
            baseRequest.setStartDate(LocalDateTime.now().plusDays(5));
            baseRequest.setEndDate(LocalDateTime.now());

            assertThrows(AppException.class, () -> promotionService.create(baseRequest));
        }

        @Test
        @DisplayName("PERCENT > 100 → PROMOTION_INVALID_PERCENT")
        void phanTramQua100() {
            baseRequest.setDiscountType(PromotionDiscountType.PERCENT);
            baseRequest.setDiscountValue(150L);

            assertThrows(AppException.class, () -> promotionService.create(baseRequest));
        }

        @Test
        @DisplayName("D18: scope BRAND lưu chữ HOA")
        void brandChuanHoaHoa() {
            stubSave();
            baseRequest.setScopeType(ScopeType.BRAND);
            baseRequest.setScopeValues(new ArrayList<>(List.of("asus")));

            PromotionResponse res = promotionService.create(baseRequest);

            assertEquals(ScopeType.BRAND, res.getScopeType());
            assertEquals(List.of("ASUS"), res.getScopeValues());
        }

        @Test
        @DisplayName("Scope ALL bỏ qua giá trị scopeValues")
        void scopeAllLuuNguyenGiaTri() {
            stubSave();

            PromotionResponse res = promotionService.create(baseRequest);

            // D17: service CRUD thuần, không "dọn" scopeValues — engine mới là nơi
            // diễn giải ALL = giảm cả giỏ. Giữ nguyên để admin đổi scope qua lại
            // không mất cấu hình đã nhập.
            assertEquals(ScopeType.ALL, res.getScopeType());
            assertEquals(List.of("ignored"), res.getScopeValues());
        }

        @Test
        @DisplayName("Exclude sản phẩm được lưu")
        void luuExclude() {
            stubSave();
            baseRequest.setExcludeProductIds(new ArrayList<>(List.of("prod-x")));

            PromotionResponse res = promotionService.create(baseRequest);

            assertEquals(1, res.getExcludeProductIds().size());
            assertEquals("prod-x", res.getExcludeProductIds().get(0));
        }
    }

    @Nested
    @DisplayName("Cập nhật chương trình")
    class Update {

        @Test
        @DisplayName("Không tìm thấy → PROMOTION_NOT_FOUND")
        void khongTimThay() {
            when(promotionRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> promotionService.update("missing", updateRequest()));
        }

        @Test
        @DisplayName("Cập nhật thay field và thay scope cũ")
        void capNhatThayScope() {
            Promotion existing = new Promotion();
            existing.setId("promo-1");
            existing.setName("Cũ");
            existing.setScopes(new ArrayList<>());
            existing.setExcludes(new ArrayList<>());
            when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(existing));
            stubSave();

            PromotionResponse res = promotionService.update("promo-1", updateRequest());

            assertEquals("Sale tháng 9", res.getName());
        }
    }

    @Nested
    @DisplayName("Ngừng áp dụng")
    class Deactivate {

        @Test
        @DisplayName("Set active=false và lưu")
        void ngungApDung() {
            Promotion existing = new Promotion();
            existing.setId("promo-1");
            existing.setActive(true);
            when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(existing));
            stubSave();

            promotionService.deactivate("promo-1");

            assertEquals(false, existing.isActive());
            verify(promotionRepository).save(existing);
        }
    }

    @Nested
    @DisplayName("Đọc danh sách")
    class Read {

        @Test
        @DisplayName("getById trả response đầy đủ scope + exclude")
        void getById() {
            Promotion p = new Promotion();
            p.setId("promo-1");
            p.setName("Sale");
            p.setScopes(new ArrayList<>());
            p.setExcludes(new ArrayList<>());
            PromotionScope scope = new PromotionScope();
            scope.setPromotion(p);
            scope.setTargetType(ScopeType.CATEGORY);
            scope.setTargetValue("cat-1");
            p.getScopes().add(scope);
            PromotionExclude ex = new PromotionExclude();
            ex.setPromotion(p);
            ex.setProductId("prod-1");
            p.getExcludes().add(ex);
            when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(p));

            PromotionResponse res = promotionService.getById("promo-1");

            assertEquals(ScopeType.CATEGORY, res.getScopeType());
            assertEquals(List.of("cat-1"), res.getScopeValues());
            assertEquals("prod-1", res.getExcludeProductIds().get(0));
        }
    }
}
