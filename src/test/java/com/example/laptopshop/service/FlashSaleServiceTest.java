package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.FlashSale;
import com.example.laptopshop.domain.FlashSaleItem;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.FlashSale.FlashSaleCreationRequest;
import com.example.laptopshop.dto.request.FlashSale.FlashSaleItemRequest;
import com.example.laptopshop.dto.response.Client.FlashPriceView;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.FlashSaleItemRepository;
import com.example.laptopshop.repository.FlashSaleRepository;
import com.example.laptopshop.repository.ProductRepository;

/**
 * Test FlashSaleService — nguồn giá flash (§3.1b) và các quyết định D26/D29/D32.
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Mock
    private FlashSaleRepository flashSaleRepository;
    @Mock
    private FlashSaleItemRepository flashSaleItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UploadService uploadService;

    @InjectMocks
    private FlashSaleService flashSaleService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId("prod-1");
        product.setName("Laptop A");
        product.setPrice(20_000_000L);
        // Tồn kho thật đủ lớn để flashStock trong các fixture (10–20) không vượt.
        product.setQuantity(50);
    }

    private FlashSale sale(String id, LocalDateTime endAt) {
        FlashSale sale = new FlashSale();
        sale.setId(id);
        sale.setName("Phiên " + id);
        sale.setStartAt(NOW.minusHours(1));
        sale.setEndAt(endAt);
        sale.setActive(true);
        sale.setItems(new ArrayList<>());
        return sale;
    }

    private FlashSaleItem item(String id, FlashSale sale, int stock, int sold, long flashPrice) {
        FlashSaleItem item = new FlashSaleItem();
        item.setId(id);
        item.setFlashSale(sale);
        item.setProduct(product);
        item.setFlashStock(stock);
        item.setSoldInFlash(sold);
        item.setFlashPrice(flashPrice);
        return item;
    }

    private FlashSaleItemRequest itemRequest(String productId, long price, int stock) {
        FlashSaleItemRequest req = new FlashSaleItemRequest();
        req.setProductId(productId);
        req.setFlashPrice(price);
        req.setFlashStock(stock);
        // Trần mỗi khách là BẮT BUỘC (V16) — mặc định 1 cho fixture.
        req.setPerUserLimit(1);
        return req;
    }

    private FlashSaleCreationRequest createRequest() {
        FlashSaleCreationRequest req = new FlashSaleCreationRequest();
        req.setName("Flash 12h");
        req.setStartAt(NOW);
        req.setEndAt(NOW.plusHours(2));
        req.setItems(new ArrayList<>(List.of(itemRequest("prod-1", 15_000_000L, 10))));
        return req;
    }

    // ==================================================================
    // §3.1b — resolvePriceMap: nguồn giá flash duy nhất cho card/giỏ/chốt
    // ==================================================================

    @Nested
    @DisplayName("resolvePriceMap")
    class ResolvePriceMap {

        @Test
        @DisplayName("Danh sách rỗng/null → map rỗng, không gọi DB")
        void danhSachRong() {
            assertTrue(flashSaleService.resolvePriceMap(List.of(), NOW).isEmpty());
            assertTrue(flashSaleService.resolvePriceMap(null, NOW).isEmpty());
            verify(flashSaleItemRepository, never()).findCurrentByProductIds(any(), any());
        }

        @Test
        @DisplayName("Lọc trùng id trước khi hỏi DB (R19 — 1 câu IN)")
        void locTrungId() {
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of());

            flashSaleService.resolvePriceMap(List.of("prod-1", "prod-1", "prod-1"), NOW);

            verify(flashSaleItemRepository).findCurrentByProductIds(List.of("prod-1"), NOW);
        }

        @Test
        @DisplayName("Item còn kho → map có giá flash + endAt cho FE đếm ngược")
        void conKho_coGiaFlash() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(item("item-1", sale, 10, 3, 15_000_000L)));

            Map<String, FlashPriceView> map = flashSaleService.resolvePriceMap(List.of("prod-1"), NOW);

            FlashPriceView view = map.get("prod-1");
            assertEquals("item-1", view.itemId());
            assertEquals("sale-1", view.flashSaleId());
            assertEquals(15_000_000L, view.flashPrice());
            assertEquals(NOW.plusHours(1), view.endAt());
            assertTrue(view.hasUsableFlashPrice());
            // Không truyền userId → chưa xét giới hạn/người.
            assertNull(view.perUserLimitLeft());
        }

        @Test
        @DisplayName("Hết kho phiên → vắng khỏi map để chỗ gọi dùng giá thường (D27)")
        void hetKho_vangKhoiMap() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(item("item-1", sale, 0, 5, 15_000_000L)));

            assertTrue(flashSaleService.resolvePriceMap(List.of("prod-1"), NOW).isEmpty());
        }

        @Test
        @DisplayName("2 phiên cùng sản phẩm → lấy phiên kết thúc sớm hơn (DB đã ORDER BY)")
        void haiPhien_layPhienKetThucSom() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(
                            item("item-1", sale, 10, 0, 15_000_000L),
                            item("item-2", sale("sale-2", NOW.plusHours(3)), 10, 0, 12_000_000L)));

            Map<String, FlashPriceView> map = flashSaleService.resolvePriceMap(List.of("prod-1"), NOW);

            assertEquals(1, map.size());
            assertEquals("item-1", map.get("prod-1").itemId());
        }

        @Test
        @DisplayName("Có userId + perUserLimit → trừ số đã mua trong phiên (D32)")
        void coUser_tinhPerUserLimit() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            FlashSaleItem flashItem = item("item-1", sale, 10, 0, 15_000_000L);
            flashItem.setPerUserLimit(2);
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(flashItem));
            // BR-F14: đếm GỘP theo phiên — 1 query cho cả nhóm item.
            when(flashSaleItemRepository.sumQtyBoughtByUserForItems(
                    "user-1", List.of("item-1")))
                    .thenReturn(List.<Object[]>of(new Object[] { "item-1", 1L }));

            FlashPriceView view = flashSaleService
                    .resolvePriceMap(List.of("prod-1"), "user-1", NOW).get("prod-1");

            assertEquals(1, view.perUserLimitLeft());
        }

        @Test
        @DisplayName("BUG: khách CHƯA mua gì → perUserLimitLeft = trần (không phải null)")
        void chuaMuaGi_vanCoTran() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            FlashSaleItem flashItem = item("item-1", sale, 10, 0, 15_000_000L);
            flashItem.setPerUserLimit(2);
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(flashItem));
            // Query đếm trả RỖNG: khách chưa mua lần nào.
            when(flashSaleItemRepository.sumQtyBoughtByUserForItems(anyString(), anyList()))
                    .thenReturn(List.of());

            FlashPriceView view = flashSaleService
                    .resolvePriceMap(List.of("prod-1"), "user-1", NOW).get("prod-1");

            // Trước đây trả null → bị hiểu là "không giới hạn" → khách mua vượt trần.
            assertEquals(2, view.perUserLimitLeft(), "0 đã mua → còn đúng bằng trần");
            assertFalse(view.allowsFlashFor(3), "vượt trần thì phải chặn");
            assertTrue(view.allowsFlashFor(2), "bằng trần thì vẫn cho");
        }

        @Test
        @DisplayName("Mua vượt limit → perUserLimitLeft = 0, không âm")
        void muaVuotLimit_san0() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            FlashSaleItem flashItem = item("item-1", sale, 10, 0, 15_000_000L);
            flashItem.setPerUserLimit(1);
            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1"), NOW))
                    .thenReturn(List.of(flashItem));
            when(flashSaleItemRepository.sumQtyBoughtByUserForItems(
                    anyString(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[] { "item-1", 5L }));

            FlashPriceView view = flashSaleService
                    .resolvePriceMap(List.of("prod-1"), "user-1", NOW).get("prod-1");

            assertEquals(0, view.perUserLimitLeft());
        }

        @Test
        @DisplayName("BR-F14: 2 sản phẩm cùng phiên → CHỈ 1 query đếm, không N+1")
        void nhieuSanPhamCungPhien_motQuery() {
            FlashSale sale = sale("sale-1", NOW.plusHours(1));
            FlashSaleItem item1 = item("item-1", sale, 10, 0, 15_000_000L);
            FlashSaleItem item2 = item("item-2", sale, 10, 0, 12_000_000L);
            item1.setPerUserLimit(3);
            item2.setPerUserLimit(3);
            // item() dùng chung field `product` → phải gán product RIÊNG cho item2,
            // nếu không cả hai cùng trỏ một object và map chỉ có 1 khoá.
            Product product2 = new Product();
            product2.setId("prod-2");
            product2.setName("Laptop B");
            product2.setPrice(18_000_000L);
            product2.setQuantity(50);
            item2.setProduct(product2);

            when(flashSaleItemRepository.findCurrentByProductIds(List.of("prod-1", "prod-2"), NOW))
                    .thenReturn(List.of(item1, item2));
            when(flashSaleItemRepository.sumQtyBoughtByUserForItems(
                    "user-1", List.of("item-1", "item-2")))
                    .thenReturn(List.<Object[]>of(new Object[] { "item-1", 1L }));

            Map<String, FlashPriceView> map = flashSaleService
                    .resolvePriceMap(List.of("prod-1", "prod-2"), "user-1", NOW);

            assertEquals(2, map.get("prod-1").perUserLimitLeft(), "3 - 1 đã mua");
            assertEquals(3, map.get("prod-2").perUserLimitLeft(), "chưa mua lần nào → giữ nguyên 3");
            // Cùng phiên → gọi đúng MỘT lần, không phải 2.
            verify(flashSaleItemRepository, times(1))
                    .sumQtyBoughtByUserForItems(anyString(), anyList());
        }
    }

    // ==================================================================
    // D29 — kho phiên atomic: 0 dòng = cạn, chỗ gọi tự fallback
    // ==================================================================

    @Nested
    @DisplayName("Trừ kho phiên")
    class ConsumeStock {

        @Test
        @DisplayName("DB trả 1 dòng → trừ thành công")
        void truThanhCong() {
            when(flashSaleItemRepository.consumeStock("item-1", 2L)).thenReturn(1);

            assertTrue(flashSaleService.consumeStock("item-1", 2L));
        }

        @Test
        @DisplayName("DB trả 0 dòng (cạn giữa lúc chốt) → false, KHÔNG throw (D27)")
        void canKho_traFalse() {
            when(flashSaleItemRepository.consumeStock("item-1", 2L)).thenReturn(0);

            assertFalse(flashSaleService.consumeStock("item-1", 2L));
        }
    }

    // ==================================================================
    // D32 — hết suất thì dòng về giá thường, KHÔNG chặn đơn
    // ==================================================================

    @Nested
    @DisplayName("allowsFlashFor (D32)")
    class AllowsFlashFor {

        private FlashPriceView view(Long flashPrice, Integer limitLeft) {
            return new FlashPriceView("item-1", "sale-1", flashPrice, 10, 0, NOW.plusHours(1), null, limitLeft);
        }

        @Test
        @DisplayName("Chưa xét theo khách (limitLeft null) → cho dùng giá flash")
        void chuaXetTheoKhach() {
            assertTrue(view(15_000_000L, null).allowsFlashFor(3));
        }

        @Test
        @DisplayName("Còn đủ suất cho cả dòng → dùng giá flash")
        void conDuSuat() {
            assertTrue(view(15_000_000L, 2).allowsFlashFor(2));
        }

        @Test
        @DisplayName("Suất còn 0 (đã mua hết) → về giá thường")
        void hetSuat() {
            assertFalse(view(15_000_000L, 0).allowsFlashFor(1));
        }

        @Test
        @DisplayName("Giỏ 3 máy nhưng chỉ còn suất 1 → cả dòng về giá thường, không tách giá")
        void muaNhieuHonSuatConLai() {
            assertFalse(view(15_000_000L, 1).allowsFlashFor(3));
        }

        @Test
        @DisplayName("Không có giá flash → không dùng (fallback giá thường)")
        void khongCoGiaFlash() {
            assertFalse(view(null, 5).allowsFlashFor(1));
        }
    }

    // ==================================================================
    // Validate admin (D26 + cấu trúc request)
    // ==================================================================

    @Nested
    @DisplayName("Tạo phiên — validate")
    class Create {

        @Test
        @DisplayName("Hợp lệ → lưu, soldInFlash khởi tạo 0")
        void taoHopLe() {
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(flashSaleRepository.save(any(FlashSale.class))).thenAnswer(inv -> {
                FlashSale s = inv.getArgument(0);
                s.setId("sale-1");
                return s;
            });

            var res = flashSaleService.create(createRequest());

            assertEquals("sale-1", res.getId());
            assertEquals(1, res.getItemCount());
            assertEquals(0, res.getItems().get(0).getSoldInFlash());
            assertEquals(20_000_000L, res.getItems().get(0).getRegularPrice());
        }

        @Test
        @DisplayName("D26: giá flash >= giá thường → FLASH_PRICE_NOT_LOWER")
        void giaFlashKhongReHon() {
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashPrice(20_000_000L);

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_PRICE_NOT_LOWER, ex.getErrorCode());
        }

        @Test
        @DisplayName("TASK-002: flashStock > tồn kho thật → FLASH_STOCK_EXCEEDS_PRODUCT_STOCK")
        void khoVuotTonKho() {
            product.setQuantity(5);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashStock(10); // > tồn kho 5

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_STOCK_EXCEEDS_PRODUCT_STOCK, ex.getErrorCode());
        }

        @Test
        @DisplayName("TASK-001: trùng khung giờ + cùng sản phẩm với phiên chưa kết thúc → FLASH_SALE_OVERLAP")
        void trungPhien() {
            when(flashSaleRepository.findOverlapping(anyString(), any(), any(), any(), anyList()))
                    .thenReturn(List.of(sale("sale-other", NOW.plusHours(1))));

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(createRequest()));
            assertEquals(ErrorCode.FLASH_SALE_OVERLAP, ex.getErrorCode());
        }

        @Test
        @DisplayName("Thiếu tên → FLASH_SALE_NAME_REQUIRED")
        void thieuTen() {
            FlashSaleCreationRequest req = createRequest();
            req.setName("  ");

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_SALE_NAME_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("endAt không sau startAt → INVALID_FLASH_SALE_DATE_RANGE")
        void khoangThoiGianSai() {
            FlashSaleCreationRequest req = createRequest();
            req.setEndAt(req.getStartAt());

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.INVALID_FLASH_SALE_DATE_RANGE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Không có sản phẩm → FLASH_SALE_NO_ITEMS")
        void khongCoSanPham() {
            FlashSaleCreationRequest req = createRequest();
            req.setItems(new ArrayList<>());

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_SALE_NO_ITEMS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Trùng sản phẩm → FLASH_SALE_ITEM_DUPLICATE (chặn trước khi đụng unique index)")
        void trungSanPham() {
            FlashSaleCreationRequest req = createRequest();
            req.setItems(new ArrayList<>(List.of(
                    itemRequest("prod-1", 15_000_000L, 10),
                    itemRequest("prod-1", 14_000_000L, 5))));

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_SALE_ITEM_DUPLICATE, ex.getErrorCode());
        }

        @Test
        @DisplayName("flashPrice <= 0 → INVALID_FLASH_PRICE")
        void giaKhongDuong() {
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashPrice(0L);

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.INVALID_FLASH_PRICE, ex.getErrorCode());
        }

        @Test
        @DisplayName("flashStock <= 0 → INVALID_FLASH_STOCK")
        void khoKhongDuong() {
            FlashSaleCreationRequest req = createRequest();
            // Số suất CÒN LẠI: 0 hợp lệ (hết suất), chỉ âm mới sai.
            req.getItems().get(0).setFlashStock(-1);

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.INVALID_FLASH_STOCK, ex.getErrorCode());
        }

        @Test
        @DisplayName("TASK-003: tối đa/khách > suất còn lại → FLASH_PER_USER_LIMIT_EXCEEDS_STOCK")
        void gioiHanKhachVuotSuat() {
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashStock(3);
            req.getItems().get(0).setPerUserLimit(5); // 5 > 3

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_PER_USER_LIMIT_EXCEEDS_STOCK, ex.getErrorCode());
        }

        @Test
        @DisplayName("TASK-003: tối đa/khách bằng suất còn lại → hợp lệ")
        void gioiHanKhachBangSuat() {
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
            when(flashSaleRepository.save(any(FlashSale.class))).thenAnswer(inv -> {
                FlashSale s = inv.getArgument(0);
                s.setId("sale-1");
                return s;
            });
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashStock(3);
            req.getItems().get(0).setPerUserLimit(3); // bằng nhau → OK

            var res = flashSaleService.create(req);
            assertEquals(1, res.getItemCount());
        }

        @Test
        @DisplayName("V16: để trống tối đa/khách → FLASH_PER_USER_LIMIT_REQUIRED")
        void gioiHanKhachNull() {
            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashStock(1);
            req.getItems().get(0).setPerUserLimit(null);

            AppException ex = assertThrows(AppException.class, () -> flashSaleService.create(req));
            assertEquals(ErrorCode.FLASH_PER_USER_LIMIT_REQUIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Sản phẩm không tồn tại → FLASH_SALE_PRODUCT_NOT_FOUND")
        void sanPhamKhongTonTai() {
            when(productRepository.findById("prod-1")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> flashSaleService.create(createRequest()));
            assertEquals(ErrorCode.FLASH_SALE_PRODUCT_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==================================================================
    // Update / Delete
    // ==================================================================

    @Nested
    @DisplayName("Cập nhật phiên")
    class Update {

        @Test
        @DisplayName("Trùng sản phẩm cũ → GIỮ soldInFlash, chỉ đổi giá/kho/limit")
        void giuSoldInFlash() {
            FlashSale existing = sale("sale-1", NOW.plusHours(2));
            FlashSaleItem old = item("item-1", existing, 10, 4, 15_000_000L);
            existing.getItems().add(old);
            when(flashSaleRepository.findById("sale-1")).thenReturn(Optional.of(existing));
            when(flashSaleRepository.save(any(FlashSale.class))).thenAnswer(inv -> inv.getArgument(0));

            FlashSaleCreationRequest req = createRequest();
            req.getItems().get(0).setFlashStock(20);

            var res = flashSaleService.update("sale-1", req);

            assertEquals(1, res.getItemCount());
            assertEquals(4, res.getItems().get(0).getSoldInFlash());
            assertEquals(20, res.getItems().get(0).getFlashStock());
            assertEquals(20, res.getItems().get(0).getRemainingStock());
        }

        @Test
        @DisplayName("Không tìm thấy → FLASH_SALE_NOT_FOUND")
        void khongTimThay() {
            when(flashSaleRepository.findById("missing")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> flashSaleService.update("missing", createRequest()));
            assertEquals(ErrorCode.FLASH_SALE_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Gỡ phiên")
    class Delete {

        @Test
        @DisplayName("Tắt active TRƯỚC khi xóa mềm — active mới là điều kiện chặn thật")
        void tatActiveRoiMoiXoa() {
            FlashSale existing = sale("sale-1", NOW.plusHours(2));
            when(flashSaleRepository.findById("sale-1")).thenReturn(Optional.of(existing));

            flashSaleService.deleteFlashSale("sale-1");

            assertFalse(existing.isActive());
            verify(flashSaleRepository).save(existing);
            verify(flashSaleRepository).delete(existing);
        }
    }

    @Nested
    @DisplayName("Hoàn kho phiên")
    class ReleaseStock {

        @Test
        @DisplayName("releaseStock đẩy thẳng xuống repository (nối D12)")
        void goiXuongRepository() {
            lenient().when(flashSaleItemRepository.releaseStock(eq("item-1"), anyLong())).thenReturn(1);

            flashSaleService.releaseStock("item-1", 2L);

            verify(flashSaleItemRepository).releaseStock("item-1", 2L);
        }
    }
}
