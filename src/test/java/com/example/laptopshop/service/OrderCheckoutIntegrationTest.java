package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Cart;
import com.example.laptopshop.domain.CartItem;
import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderDetail;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionType;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.domain.UserVoucher;
import com.example.laptopshop.domain.UserVoucherSource;
import com.example.laptopshop.domain.UserVoucherStatus;
import com.example.laptopshop.dto.request.Client.CreateOrderRequest;
import com.example.laptopshop.dto.response.Client.OrderDetailResponse;
import com.example.laptopshop.dto.response.Client.VoucherValidationResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CartRepository;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.ProductRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.repository.UserVoucherRepository;

/**
 * Test TÍCH HỢP cho đường đặt đơn + hủy đơn — vùng chạm tiền và kho.
 *
 * <p>
 * Vì sao cần loại test này: các unit test hiện có đều mock repository nên KHÔNG
 * bắt được lỗi kiểu "quên nối một nhánh" (đã từng xảy ra: admin hủy đơn thiếu
 * hoàn voucher). Ở đây dùng H2 thật + @Transactional để rollback sau mỗi test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCheckoutIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private UserVoucherRepository userVoucherRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private com.example.laptopshop.repository.PromotionRepository promotionRepository;
    @Autowired
    private FlashSaleTestSupport flashSaleSupport;
    @Autowired
    private com.example.laptopshop.repository.FlashSaleItemRepository flashSaleItemRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("checkout-test@example.com");
        user.setFullName("Khách Test");
        user.setPassword("x");
        user = this.userRepository.save(user);

        product = new Product();
        product.setCode("TEST-LAPTOP-1");
        product.setName("Laptop Test");
        product.setPrice(20_000_000L);
        product.setQuantity(10);
        product.setSold(0L);
        product.setFactory("ASUS");
        product.setActive(true);
        product = this.productRepository.save(product);
    }

    /** Giỏ có sẵn 1 dòng với số lượng cho trước. */
    private void addToCart(long quantity) {
        Cart cart = new Cart();
        cart.setUser(user);
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        cart.addItem(item);
        this.cartRepository.save(cart);
    }

    private CreateOrderRequest orderRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setReceiverFullName("Người Nhận");
        req.setReceiverPhone("0901234567");
        req.setReceiverAddress("123 Đường Test, Phường 1, Quận 1");
        req.setReceiverProvinceCode("79");
        req.setReceiverProvinceName("TP Hồ Chí Minh");
        req.setReceiverCommuneCode("26734");
        req.setReceiverCommuneName("Phường Bến Nghé");
        req.setPaymentMethod(PaymentMethod.COD);
        return req;
    }

    private Voucher voucher(String code, long discountAmount, Integer perUserLimit) {
        Voucher c = new Voucher();
        c.setCode(code);
        c.setDiscountAmount(discountAmount);
        c.setActive(true);
        c.setUsageLimit(100);
        c.setUsedCount(0);
        c.setVoucherType(VoucherType.PUBLIC);
        c.setPerUserLimit(perUserLimit);
        return this.voucherRepository.save(c);
    }

    private UserVoucher walletVoucher(Voucher c) {
        UserVoucher v = new UserVoucher();
        v.setUser(user);
        v.setVoucher(c);
        v.setStatus(UserVoucherStatus.AVAILABLE);
        v.setSource(UserVoucherSource.CLAIMED);
        v.setAcquiredAt(LocalDateTime.now());
        v.setExpiresAt(LocalDateTime.now().plusDays(30));
        return this.userVoucherRepository.save(v);
    }

    // ==================================================================
    // BR-V13 — preview voucher phải hỏi BE cho CẢ hai nhánh (mã + ví)
    // ==================================================================

    @Test
    @DisplayName("BR-V13: validate voucher TỪ VÍ trả đúng số giảm (trước đây không có đường này)")
    void validateVoucherTuVi() {
        addToCart(1);
        Voucher c = voucher("VI10", 0L, null);
        c.setDiscountAmount(null);
        c.setDiscountPercent(10);
        this.voucherRepository.save(c);
        UserVoucher v = walletVoucher(c);

        com.example.laptopshop.dto.request.Client.ValidateVoucherRequest req =
                new com.example.laptopshop.dto.request.Client.ValidateVoucherRequest();
        req.setUserVoucherId(v.getId());

        VoucherValidationResponse res = orderService.validateVoucher(user.getId(), req);

        assertTrue(res.isValid());
        // Giỏ 20tr × 1 → 10% = 2.000.000.
        assertEquals(2_000_000L, res.getDiscountAmount());
    }

    @Test
    @DisplayName("BR-V13: voucher từ ví có PHẠM VI không khớp → BE chặn (FE từng tính sai vì bỏ qua scope)")
    void validateVoucherTuVi_scopeKhongKhop() {
        addToCart(1);
        // Sản phẩm ASUS; voucher chỉ áp BRAND=DELL → eligibleAmount = 0.
        Voucher c = voucher("DELLVIP", 0L, null);
        c.setDiscountAmount(null);
        c.setDiscountPercent(50);
        c.setScopeType(com.example.laptopshop.domain.ScopeType.BRAND);
        var scope = new com.example.laptopshop.domain.VoucherScope();
        scope.setVoucher(c);
        scope.setTargetType(com.example.laptopshop.domain.ScopeType.BRAND);
        scope.setTargetValue("DELL");
        c.getScopes().add(scope);
        this.voucherRepository.save(c);
        UserVoucher v = walletVoucher(c);

        com.example.laptopshop.dto.request.Client.ValidateVoucherRequest req =
                new com.example.laptopshop.dto.request.Client.ValidateVoucherRequest();
        req.setUserVoucherId(v.getId());

        VoucherValidationResponse res = orderService.validateVoucher(user.getId(), req);

        // FE cũ tính 50% × 20tr = 10tr và cho khách qua → BE chặn, lệch 10 triệu.
        assertFalse(res.isValid(), "Phạm vi không khớp thì không được báo hợp lệ");
        assertEquals(0L, res.getDiscountAmount());
    }

    @Test
    @DisplayName("BR-V13: voucher trong ví của khách KHÁC → không tìm thấy")
    void validateVoucherViCuaNguoiKhac() {
        addToCart(1);
        Voucher c = voucher("OTHERVIP", 100_000L, null);
        // Ví thuộc về một khách KHÁC (user_id là NOT NULL nên phải tạo user thật).
        User other = new User();
        other.setEmail("other-vip@example.com");
        other.setFullName("Khách Khác");
        other.setPassword("x");
        other = this.userRepository.save(other);

        UserVoucher v = new UserVoucher();
        v.setUser(other);
        v.setVoucher(c);
        v.setStatus(UserVoucherStatus.AVAILABLE);
        v.setSource(UserVoucherSource.CLAIMED);
        v.setAcquiredAt(LocalDateTime.now());
        v.setExpiresAt(LocalDateTime.now().plusDays(30));
        v = this.userVoucherRepository.save(v);

        com.example.laptopshop.dto.request.Client.ValidateVoucherRequest req =
                new com.example.laptopshop.dto.request.Client.ValidateVoucherRequest();
        req.setUserVoucherId(v.getId());

        VoucherValidationResponse res = orderService.validateVoucher(user.getId(), req);

        assertFalse(res.isValid(), "Voucher của khách khác không được báo hợp lệ");
    }

    // ==================================================================
    // Đặt đơn — giá + kho
    // ==================================================================

    @Test
    @DisplayName("Đặt đơn trừ tồn kho và tăng sold")
    void datDon_truTonKho() {
        addToCart(2);

        OrderDetailResponse res = orderService.createOrder(user.getId(), orderRequest());

        assertNotNull(res);
        // Trừ kho nay là UPDATE atomic (BR-A04) nên KHÔNG đi qua persistence
        // context → phải clear trước khi đọc lại, nếu không thấy giá trị cache cũ.
        this.entityManager.flush();
        this.entityManager.clear();
        Product after = this.productRepository.findById(product.getId()).orElseThrow();
        assertEquals(8, after.getQuantity());
        assertEquals(2L, after.getSold());
    }

    @Test
    @DisplayName("Giỏ trống → CART_EMPTY")
    void gioTrong() {
        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), orderRequest()));
        assertEquals(ErrorCode.CART_EMPTY, ex.getErrorCode());
    }

    @Test
    @DisplayName("Số lượng vượt tồn kho → CART_QUANTITY_EXCEEDS_STOCK")
    void vuotTonKho() {
        addToCart(99);

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), orderRequest()));
        assertEquals(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK, ex.getErrorCode());
    }

    // ==================================================================
    // Voucher — D11, D22, D15
    // ==================================================================

    @Test
    @DisplayName("Dùng voucher trong ví → ghi voucherDiscount, đánh dấu USED, gắn đơn")
    void dungVoucherVi() {
        addToCart(1);
        Voucher c = voucher("VIP50", 500_000L, null);
        UserVoucher v = walletVoucher(c);
        CreateOrderRequest req = orderRequest();
        req.setUserVoucherId(v.getId());

        OrderDetailResponse res = orderService.createOrder(user.getId(), req);

        assertEquals(500_000L, res.getVoucherDiscount());
        UserVoucher after = this.userVoucherRepository.findById(v.getId()).orElseThrow();
        assertEquals(UserVoucherStatus.USED, after.getStatus());
        assertNotNull(after.getUsedAt());
        assertNotNull(after.getOrder());
        // usageLimit toàn hệ thống vẫn tăng cho cả đường ví. usedCount tăng bằng
        // UPDATE atomic (BR-A03) → clear context trước khi đọc lại.
        this.entityManager.flush();
        this.entityManager.clear();
        assertEquals(1, this.voucherRepository.findById(c.getId()).orElseThrow().getUsedCount());
    }

    @Test
    @DisplayName("D11: gửi cả voucherCode lẫn userVoucherId → VOUCHER_AND_VOUCHER_CONFLICT")
    void guiCaHaiLoaiMa() {
        addToCart(1);
        Voucher c = voucher("CONFLICT", 100_000L, null);
        UserVoucher v = walletVoucher(c);
        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("CONFLICT");
        req.setUserVoucherId(v.getId());

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        assertEquals(ErrorCode.VOUCHER_AND_VOUCHER_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Dùng voucher của khách KHÁC → USER_VOUCHER_NOT_FOUND")
    void voucherCuaNguoiKhac() {
        addToCart(1);
        Voucher c = voucher("OTHER", 100_000L, null);

        User other = new User();
        other.setEmail("other@example.com");
        other.setFullName("Người Khác");
        other.setPassword("x");
        other = this.userRepository.save(other);
        UserVoucher v = new UserVoucher();
        v.setUser(other);
        v.setVoucher(c);
        v.setStatus(UserVoucherStatus.AVAILABLE);
        v.setSource(UserVoucherSource.CLAIMED);
        v.setAcquiredAt(LocalDateTime.now());
        v = this.userVoucherRepository.save(v);

        CreateOrderRequest req = orderRequest();
        req.setUserVoucherId(v.getId());

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        assertEquals(ErrorCode.USER_VOUCHER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("D15: gõ mã đã đạt perUserLimit → VOUCHER_PER_USER_LIMIT_REACHED")
    void goMaQuaGioiHanMoiKhach() {
        addToCart(1);
        // perUserLimit=1 nhưng đã có 1 đơn trước đó dùng mã này.
        Voucher c = voucher("LIMIT1", 100_000L, 1);
        Order old = new Order();
        old.setOrderCode("OLD-001");
        old.setUser(user);
        old.setVoucher(c);
        old.setStatus(OrderStatus.COMPLETED);
        old.setOrderDate(LocalDateTime.now());
        old.setDiscountAmount(0L);
        old.setShippingFee(0L);
        old.setTotalPrice(0L);
        this.orderRepository.save(old);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("LIMIT1");

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        // Trả ĐÚNG nhánh vi phạm (không còn gộp thành VOUCHER_NOT_USABLE chung).
        assertEquals(ErrorCode.VOUCHER_PER_USER_LIMIT_REACHED, ex.getErrorCode());
    }

    @Test
    @DisplayName("D22: minOrderValue xét trên tiền hàng khớp scope, không phải cả giỏ")
    void minOrderValueTheoScope() {
        // Sản phẩm ASUS giá 20tr; voucher scope ALL (khớp cả giỏ) nhưng yêu cầu
        // đơn từ 25tr → tiền khớp scope = 20tr < 25tr → chặn vì CHƯA ĐỦ TỐI THIỂU.
        // (Giỏ 20tr vẫn "to" nhưng ngưỡng xét trên tiền khớp phạm vi, không phải
        // một con số khác.)
        addToCart(1);
        Voucher c = voucher("MINHIGH", 100_000L, null);
        c.setScopeType(com.example.laptopshop.domain.ScopeType.ALL);
        c.setMinOrderValue(25_000_000L);
        this.voucherRepository.save(c);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("MINHIGH");

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        assertEquals(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET, ex.getErrorCode());
    }

    @Test
    @DisplayName("D22: scope không khớp dòng nào → VOUCHER_NO_ELIGIBLE_ITEM (không phải lỗi tối thiểu)")
    void scopeKhongKhopDongNao() {
        // Sản phẩm hãng ASUS; voucher scope BRAND=Dell → tiền khớp scope = 0 →
        // chặn vì KHÔNG CÓ SẢN PHẨM NÀO THUỘC PHẠM VI, khác hẳn lỗi chưa đủ tối thiểu.
        addToCart(1);
        Voucher c = voucher("DELLONLY", 100_000L, null);
        c.setScopeType(com.example.laptopshop.domain.ScopeType.BRAND);
        var scope = new com.example.laptopshop.domain.VoucherScope();
        scope.setVoucher(c);
        scope.setTargetType(com.example.laptopshop.domain.ScopeType.BRAND);
        scope.setTargetValue("DELL");
        c.getScopes().add(scope);
        c.setMinOrderValue(10_000_000L);
        this.voucherRepository.save(c);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("DELLONLY");

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        assertEquals(ErrorCode.VOUCHER_NO_ELIGIBLE_ITEM, ex.getErrorCode());
    }

    // ==================================================================
    // Hủy đơn — D12 (bug từng bị bỏ sót ở nhánh admin)
    // ==================================================================

    @Test
    @DisplayName("D12: khách tự hủy → voucher về ví, usedCount giảm, kho hoàn")
    void khachHuyDon_hoanDu() {
        addToCart(2);
        Voucher c = voucher("CANCEL1", 300_000L, null);
        UserVoucher v = walletVoucher(c);
        CreateOrderRequest req = orderRequest();
        req.setUserVoucherId(v.getId());

        OrderDetailResponse created = orderService.createOrder(user.getId(), req);
        // createOrder chạy trong transaction của nó; đẩy hết xuống DB và xoá
        // persistence context để lượt hủy đọc lại trạng thái mới nhất.
        this.entityManager.flush();
        this.entityManager.clear();

        orderService.cancelMyOrder(user.getId(), created.getId());
        // Hoàn kho + usedCount nay là UPDATE atomic (BR-A03/BR-A04) → clear
        // context để đọc lại trạng thái mới nhất từ DB.
        this.entityManager.flush();
        this.entityManager.clear();

        // Kho hoàn lại.
        Product after = this.productRepository.findById(product.getId()).orElseThrow();
        assertEquals(10, after.getQuantity());
        assertEquals(0L, after.getSold());
        // Voucher về ví, bỏ dấu đơn.
        UserVoucher afterVoucher = this.userVoucherRepository.findById(v.getId()).orElseThrow();
        assertEquals(UserVoucherStatus.AVAILABLE, afterVoucher.getStatus());
        assertNull(afterVoucher.getOrder());
        assertNull(afterVoucher.getUsedAt());
        // usedCount giảm về 0.
        assertEquals(0, this.voucherRepository.findById(c.getId()).orElseThrow().getUsedCount());
    }

    @Test
    @DisplayName("D12: ADMIN hủy đơn → cũng hoàn voucher + usedCount (bug đã sửa)")
    void adminHuyDon_hoanDu() {
        addToCart(2);
        Voucher c = voucher("CANCEL2", 300_000L, null);
        UserVoucher v = walletVoucher(c);
        CreateOrderRequest req = orderRequest();
        req.setUserVoucherId(v.getId());

        OrderDetailResponse created = orderService.createOrder(user.getId(), req);
        this.entityManager.flush();
        this.entityManager.clear();
        // Admin đổi trạng thái sang CANCELLED — đường đi qua applyStatusChange.
        orderService.updateOrderStatus(created.getId(), OrderStatus.CANCELLED);
        // Hoàn kho + usedCount nay là UPDATE atomic (BR-A03/BR-A04) → clear context.
        this.entityManager.flush();
        this.entityManager.clear();

        UserVoucher afterVoucher = this.userVoucherRepository.findById(v.getId()).orElseThrow();
        assertEquals(UserVoucherStatus.AVAILABLE, afterVoucher.getStatus(),
                "Admin hủy đơn phải hoàn voucher về ví");
        assertNull(afterVoucher.getOrder());
        assertEquals(0, this.voucherRepository.findById(c.getId()).orElseThrow().getUsedCount(),
                "Admin hủy đơn phải giảm usedCount");
        assertEquals(10, this.productRepository.findById(product.getId()).orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Hủy đơn 2 lần → lần 2 bị chặn (không hoàn gấp đôi)")
    void huyHaiLan_biChan() {
        addToCart(1);
        OrderDetailResponse created = orderService.createOrder(user.getId(), orderRequest());
        this.entityManager.flush();
        this.entityManager.clear();
        orderService.cancelMyOrder(user.getId(), created.getId());
        this.entityManager.flush();
        this.entityManager.clear();

        assertThrows(AppException.class,
                () -> orderService.cancelMyOrder(user.getId(), created.getId()));
        // Kho vẫn đúng 10, không bị cộng thêm lần 2.
        assertEquals(10, this.productRepository.findById(product.getId()).orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Hủy đơn SHIPPING → không cho hủy")
    void huyDonDangGiao_biChan() {
        addToCart(1);
        OrderDetailResponse created = orderService.createOrder(user.getId(), orderRequest());
        this.entityManager.flush();
        this.entityManager.clear();
        orderService.updateOrderStatus(created.getId(), OrderStatus.CONFIRMED);
        orderService.updateOrderStatus(created.getId(), OrderStatus.SHIPPING);

        AppException ex = assertThrows(AppException.class,
                () -> orderService.cancelMyOrder(user.getId(), created.getId()));
        assertEquals(ErrorCode.ORDER_CANNOT_CANCEL, ex.getErrorCode());
    }

    // ==================================================================
    // Giá flash (D25/D27/D29)
    // ==================================================================

    @Test
    @DisplayName("D27: dòng trong phiên flash → OrderDetail.price = giá flash")
    void giaFlashVaoDon() {
        addToCart(1);
        flashSaleSupport.runningSaleFor(product, 15_000_000L, 5);

        OrderDetailResponse res = orderService.createOrder(user.getId(), orderRequest());

        assertEquals(15_000_000L, res.getItems().get(0).getPrice(),
                "Giá flash phải được snapshot vào dòng đơn");
    }

    @Test
    @DisplayName("D27: hết phiên → dòng về giá thường")
    void hetPhienVeGiaThuong() {
        addToCart(1);
        // Phiên đã kết thúc (endAt trong quá khứ).
        flashSaleSupport.saleFor(product, 15_000_000L, 5,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));

        OrderDetailResponse res = orderService.createOrder(user.getId(), orderRequest());

        assertEquals(20_000_000L, res.getItems().get(0).getPrice());
    }

    @Test
    @DisplayName("D29: flashStock=1, mua 1 → trừ kho phiên, ghi flashSaleItemId để hủy còn hoàn")
    void truKhoPhienVaGhiId() {
        addToCart(1);
        var sale = flashSaleSupport.runningSaleFor(product, 15_000_000L, 1);
        String itemId = sale.getItems().get(0).getId();

        orderService.createOrder(user.getId(), orderRequest());

        // consumeStock là JPQL UPDATE thẳng xuống DB nên object trong bộ nhớ
        // KHÔNG được cập nhật — phải clear + đọc lại từ DB.
        this.entityManager.flush();
        this.entityManager.clear();
        var item = this.flashSaleItemRepository.findById(itemId).orElseThrow();
        assertEquals(1, item.getSoldInFlash(), "Kho phiên phải bị trừ");
        Order order = this.orderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertEquals(itemId, order.getOrderDetails().get(0).getFlashSaleItemId());
    }

    @Test
    @DisplayName("D29: kho phiên cạn giữa lúc chốt → KHÔNG fail đơn, dòng về giá thường")
    void canKhoPhien_khongFailDon() {
        addToCart(2);
        // Kho phiên chỉ 1 nhưng giỏ mua 2 → consumeStock trả 0 dòng.
        flashSaleSupport.runningSaleFor(product, 15_000_000L, 1);

        OrderDetailResponse res = orderService.createOrder(user.getId(), orderRequest());

        assertEquals(20_000_000L, res.getItems().get(0).getPrice(),
                "Hết kho phiên thì dòng về giá thường, đơn vẫn tạo được");
    }

    @Test
    @DisplayName("D32: hết suất mỗi khách → dòng về giá thường, không chặn đơn")
    void hetSuatMoiKhach() {
        addToCart(1);
        var sale = flashSaleSupport.runningSaleFor(product, 15_000_000L, 10);
        sale.getItems().get(0).setPerUserLimit(1);
        // Đơn cũ đã mua 1 máy trong cùng khung giờ phiên → hết suất.
        Order old = new Order();
        old.setOrderCode("OLD-FLASH");
        old.setUser(user);
        old.setStatus(OrderStatus.COMPLETED);
        old.setOrderDate(LocalDateTime.now());
        old.setDiscountAmount(0L);
        old.setShippingFee(0L);
        old.setTotalPrice(0L);
        OrderDetail oldDetail = new OrderDetail();
        oldDetail.setOrder(old);
        oldDetail.setProduct(product);
        oldDetail.setQuantity(1);
        oldDetail.setPrice(15_000_000L);
        old.setOrderDetails(List.of(oldDetail));
        this.orderRepository.save(old);
        flashSaleSupport.saveItems(sale);

        OrderDetailResponse res = orderService.createOrder(user.getId(), orderRequest());

        assertEquals(20_000_000L, res.getItems().get(0).getPrice(),
                "Hết suất mỗi khách thì về giá thường (D32 cách 1)");
    }

    @Test
    @DisplayName("BR-V14: voucher mệnh giá > giá trị đơn → VẪN cho dùng, kẹp ở subtotal (không chặn)")
    void voucherLonHonDon_vanChoDung() {
        addToCart(1);
        // Đơn 20tr, voucher 50tr → cho dùng, giảm hết 20tr. KHÔNG ném lỗi.
        Voucher c = voucher("BIG", 50_000_000L, null);
        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("BIG");

        OrderDetailResponse res = orderService.createOrder(user.getId(), req);

        assertEquals(20_000_000L, res.getDiscountAmount(),
                "Giảm hết tiền hàng, không vượt tạm tính");
        assertEquals(0L, res.getSubtotal() - res.getDiscountAmount(),
                "Tiền hàng về 0");
        // Voucher vẫn bị tính là đã dùng dù chỉ dùng được một phần giá trị (BR-V14).
        // incrementUsedCount là JPQL UPDATE thẳng xuống DB nên object trong bộ nhớ
        // KHÔNG được cập nhật — phải clear + đọc lại từ DB.
        this.entityManager.flush();
        this.entityManager.clear();
        assertEquals(1, this.voucherRepository.findById(c.getId()).orElseThrow().getUsedCount());
    }

    @Test
    @DisplayName("BR-V14: promotion 10k + voucher 500k trên đơn 100k → tiền hàng 0đ, chỉ còn phí ship")
    void voucherLonHonDon_khachTra0TienHang() {
        // Sản phẩm 100k, mua 1 → subtotal 100k.
        product.setPrice(100_000L);
        this.productRepository.save(product);
        addToCart(1);

        // Promotion giảm 10k/máy (AMOUNT = per-máy) → lineDiscount 10.000.
        // KHÔNG set id thủ công: Promotion.id là @GeneratedValue(UUID), gán tay
        // làm Spring Data gọi merge() (INSERT trễ) thay vì persist() → xung đột
        // optimistic-lock với incrementUsedCount (JPQL UPDATE) lúc tạo đơn.
        Promotion promo = new Promotion();
        promo.setName("Giảm 10k");
        promo.setType(PromotionType.PRODUCT_DISCOUNT);
        promo.setDiscountType(PromotionDiscountType.AMOUNT);
        promo.setDiscountValue(10_000L);
        promo.setStartDate(LocalDateTime.now().minusDays(1));
        promo.setEndDate(LocalDateTime.now().plusDays(1));
        promo.setActive(true);
        promo.setUsedCount(0);
        this.promotionRepository.save(promo);

        // Voucher "giảm thẳng 500k" — lớn hơn cả đơn.
        voucher("GIAM500K", 500_000L, null);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("GIAM500K");

        OrderDetailResponse res = orderService.createOrder(user.getId(), req);

        // eligibleAmount = 100.000 − 10.000 = 90.000 (voucher tính trên phần còn lại).
        // voucherDiscount bị kẹp ở 90.000 → tổng giảm = 100.000 = subtotal.
        assertEquals(10_000L, res.getPromotionDiscount());
        assertEquals(90_000L, res.getVoucherDiscount());
        assertEquals(100_000L, res.getDiscountAmount());
        // Kết quả cuối: hàng 100k + ship 50k − giảm 100k = 50k (chỉ còn tiền ship).
        assertEquals(50_000L, res.getTotalPrice(), "Khách trả 0đ tiền hàng, chỉ trả phí ship");
        assertTrue(res.getTotalPrice() >= 0, "Cần thanh toán không bao giờ âm");

        // Khách thấy đơn giảm nhờ chương trình NÀO, không chỉ tổng tiền.
        assertEquals(1, res.getPromotionLines().size(), "Gộp đúng 1 chương trình");
        assertEquals("Giảm 10k", res.getPromotionLines().get(0).getName());
        assertEquals(10_000L, res.getPromotionLines().get(0).getDiscountAmount());
        assertEquals("GIAM500K", res.getVoucherCode());

        // Các dòng tóm tắt phải CỘNG LẠI RA tổng: nếu FE hiện `subtotal` (đã trừ
        // giảm giá cấp dòng) lên dòng đầu thì khoản giảm bị trừ 2 lần.
        assertEquals(100_000L, res.getTotalBeforeDiscount(), "Tiền hàng gốc, chưa trừ gì");
        assertEquals(res.getTotalPrice(),
                res.getTotalBeforeDiscount() - res.getPromotionDiscount() - res.getVoucherDiscount()
                        + res.getShippingFee(),
                "Tiền hàng gốc − khuyến mại − voucher + ship = tổng cộng");

        // Dòng sản phẩm: phải giữ được ĐƠN GIÁ gốc, nếu không FE chỉ hiện thành
        // tiền và khách tưởng đơn giá là 90k (BR-V18).
        var item = res.getItems().get(0);
        assertEquals(100_000L, item.getPrice(), "Đơn giá gốc, chưa trừ khuyến mại");
        assertEquals(10_000L, item.getDiscountAmount(), "Giảm giá riêng của dòng");
        assertEquals(90_000L, item.getLineTotal(), "Thành tiền = đơn giá − giảm giá dòng");
    }

    // ==================================================================
    // BR-A03 / BR-A04 — trừ hạn mức & tồn kho phải ATOMIC
    // ==================================================================

    @Test
    @DisplayName("BR-A03: voucher hết lượt (usedCount = usageLimit) → VOUCHER_OUT_OF_STOCK, đơn không tạo")
    void voucherHetLuot_biChan() {
        addToCart(1);
        // usageLimit = 1 nhưng đã dùng hết 1 lượt (mô phỏng khách khác vừa chốt).
        Voucher c = voucher("HETLUOT", 100_000L, null);
        c.setUsageLimit(1);
        c.setUsedCount(1);
        this.voucherRepository.save(c);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("HETLUOT");

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), req));
        assertEquals(ErrorCode.VOUCHER_OUT_OF_STOCK, ex.getErrorCode());

        // Transaction rollback: kho KHÔNG bị trừ, giỏ vẫn còn.
        this.entityManager.flush();
        this.entityManager.clear();
        assertEquals(10, this.productRepository.findById(product.getId()).orElseThrow().getQuantity(),
                "Đơn lỗi không được để lại dấu vết trừ kho");
    }

    @Test
    @DisplayName("BR-A03: usedCount NULL (voucher cũ) vẫn tăng đúng 1, không báo hết lượt oan")
    void voucherUsedCountNull_vanTangDuoc() {
        addToCart(1);
        Voucher c = voucher("NULLCOUNT", 100_000L, null);
        c.setUsedCount(null); // cột thêm ở Sprint 1 → voucher cũ có thể NULL
        this.voucherRepository.save(c);

        CreateOrderRequest req = orderRequest();
        req.setVoucherCode("NULLCOUNT");

        OrderDetailResponse res = orderService.createOrder(user.getId(), req);

        assertEquals(100_000L, res.getVoucherDiscount());
        this.entityManager.flush();
        this.entityManager.clear();
        assertEquals(1, this.voucherRepository.findById(c.getId()).orElseThrow().getUsedCount(),
                "NULL + 1 phải ra 1, không phải NULL");
    }

    @Test
    @DisplayName("BR-A04: tồn kho không đủ ở bước trừ atomic → CART_QUANTITY_EXCEEDS_STOCK")
    void tonKhoKhongDu_biChan() {
        addToCart(2);
        // Kho bị khách khác lấy mất sau khi đã vào giỏ: còn 1 nhưng giỏ xin 2.
        product.setQuantity(1);
        this.productRepository.save(product);

        AppException ex = assertThrows(AppException.class,
                () -> orderService.createOrder(user.getId(), orderRequest()));
        assertEquals(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK, ex.getErrorCode());
    }

    @Test
    @DisplayName("BR-A04: đặt đơn cập nhật updatedAt của sản phẩm (không đứng yên)")
    void datDon_capNhatUpdatedAt() {
        addToCart(1);
        // updatedAt là @LastModifiedDate của Hibernate — UPDATE atomic phải tự set
        // NOW() trong câu lệnh, nếu không cột "Ngày sửa" ở admin sẽ không đổi.
        Product before = this.productRepository.findById(product.getId()).orElseThrow();
        before.setUpdatedAt(java.time.LocalDateTime.now().minusDays(3));
        this.productRepository.save(before);
        this.entityManager.flush();
        this.entityManager.clear();
        java.time.LocalDateTime oldUpdatedAt = this.productRepository.findById(product.getId())
                .orElseThrow().getUpdatedAt();

        orderService.createOrder(user.getId(), orderRequest());

        this.entityManager.flush();
        this.entityManager.clear();
        java.time.LocalDateTime newUpdatedAt = this.productRepository.findById(product.getId())
                .orElseThrow().getUpdatedAt();
        assertTrue(newUpdatedAt.isAfter(oldUpdatedAt),
                "updatedAt phải được cập nhật khi trừ kho");
    }
}
