package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Cart;
import com.example.laptopshop.domain.CartItem;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Client.AddToCartRequest;
import com.example.laptopshop.dto.request.Client.MergeCartRequest;
import com.example.laptopshop.dto.request.Client.UpdateCartItemRequest;
import com.example.laptopshop.dto.response.Client.AppliedPromotionResponse;
import com.example.laptopshop.dto.response.Client.CartItemResponse;
import com.example.laptopshop.dto.response.Client.CartResponse;
import com.example.laptopshop.dto.response.Client.FlashPriceView;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CartItemRepository;
import com.example.laptopshop.repository.CartRepository;
import com.example.laptopshop.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Giỏ hàng của khách đã đăng nhập.
 *
 * Nguyên tắc:
 * - 1 user ↔ 1 cart (lazy create: chỉ tạo khi thực sự thêm hàng, tránh sinh
 *   bản ghi rỗng mỗi lần GET).
 * - Giá KHÔNG lưu trong cart_item — luôn đọc giá hiện tại của product, nên
 *   khách thấy đúng giá ngay khi admin đổi giá.
 * - Số lượng luôn bị chặn theo tồn kho thật ({@code product.quantity}), không
 *   theo giới hạn hiển thị của FE.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartService {

    // Chính sách phí ship (hằng số minh họa, khớp mockup/DESIGN.md).
    // TODO: chuyển thành cấu hình app.shipping.* khi cần thay đổi linh hoạt.
    static final long FREE_SHIPPING_THRESHOLD = 2_000_000L;
    static final long DEFAULT_SHIPPING_FEE = 50_000L;

    CartRepository cartRepository;
    CartItemRepository cartItemRepository;
    UserRepository userRepository;
    ProductService productService;
    PromotionService promotionService;
    PromotionEngine promotionEngine;
    FlashSaleService flashSaleService;

    // ===== Truy vấn =====

    /** Lấy giỏ hiện tại; chưa có thì trả giỏ RỖNG (không ghi DB). */
    @Transactional(readOnly = true)
    public CartResponse getMyCart(String userId) {
        Cart cart = this.cartRepository.findByUserId(userId).orElse(null);
        return toResponse(cart);
    }

    /**
     * Lấy cart để ghi (tạo mới nếu chưa có). Chỉ gọi trong luồng mutate để
     * không sinh bản ghi rỗng khi khách chỉ xem giỏ.
     */
    @Transactional
    public Cart getOrCreateCart(String userId) {
        return this.cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = this.userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            Cart cart = new Cart();
            cart.setUser(user);
            return this.cartRepository.save(cart);
        });
    }

    // ===== Thao tác =====

    /**
     * Thêm sản phẩm vào giỏ. Sản phẩm đã có → cộng dồn số lượng.
     * Chặn nếu tổng số lượng vượt tồn kho.
     */
    @Transactional
    public CartResponse addItem(String userId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = this.productService.getProductById(request.getProductId());

        CartItem existing = cart.findItemByProductId(product.getId());
        long currentQty = existing == null ? 0L : existing.getQuantity();
        long targetQty = currentQty + request.getQuantity();

        validateStock(product, targetQty);
        enforceFlashPerUserLimit(userId, product, targetQty);

        if (existing == null) {
            CartItem item = new CartItem();
            item.setProduct(product);
            item.setQuantity(targetQty);
            cart.addItem(item);
        } else {
            existing.setQuantity(targetQty);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
        return toResponse(cart);
    }

    /** Đặt số lượng tuyệt đối cho 1 dòng (nút +/- trên FE gửi giá trị cuối). */
    @Transactional
    public CartResponse updateQuantity(String userId, String productId, UpdateCartItemRequest request) {
        Cart cart = this.cartRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        CartItem item = cart.findItemByProductId(productId);
        if (item == null) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        validateStock(item.getProduct(), request.getQuantity());
        enforceFlashPerUserLimit(userId, item.getProduct(), request.getQuantity());
        item.setQuantity(request.getQuantity());

        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(String userId, String productId) {
        Cart cart = this.cartRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        CartItem item = cart.findItemByProductId(productId);
        if (item == null) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        cart.removeItem(item);
        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse clear(String userId) {
        Cart cart = this.cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return toResponse(null);
        }
        cart.getItems().clear();
        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
        return toResponse(cart);
    }

    /**
     * Gộp giỏ guest (FE giữ ở localStorage) vào giỏ server ngay sau khi đăng
     * nhập. Sản phẩm trùng thì CỘNG DỒN — không tạo dòng trùng, không ghi đè
     * số lượng khách đã có trên server.
     *
     * Sản phẩm không còn tồn tại / đã ngừng bán sẽ bị BỎ QUA (không làm hỏng
     * cả thao tác merge) vì giỏ guest có thể đã cũ.
     */
    @Transactional
    public CartResponse mergeGuestCart(String userId, MergeCartRequest request) {
        Cart cart = getOrCreateCart(userId);

        if (request.getItems() != null) {
            for (MergeCartRequest.GuestCartItem guestItem : request.getItems()) {
                Product product;
                try {
                    product = this.productService.getProductById(guestItem.getProductId());
                    if (!product.isActive()) {
                        continue;
                    }
                } catch (AppException e) {
                    continue; // sản phẩm đã bị xóa — bỏ qua
                }

                CartItem existing = cart.findItemByProductId(product.getId());
                long currentQty = existing == null ? 0L : existing.getQuantity();
                // Cộng dồn nhưng không vượt tồn kho: giỏ guest cũ có thể xin
                // nhiều hơn số hàng còn lại.
                long merged = Math.min(currentQty + guestItem.getQuantity(), product.getQuantity());
                // Không vượt trần mỗi khách của phiên flash: giỏ guest (localStorage)
                // có thể được nhồi số lượng khi chưa đăng nhập, gộp vào sẽ vượt suất.
                Long flashCap = flashPerUserLimitLeft(userId, product);
                if (flashCap != null) {
                    merged = Math.min(merged, flashCap);
                }
                if (merged <= 0) {
                    continue;
                }

                if (existing == null) {
                    CartItem item = new CartItem();
                    item.setProduct(product);
                    item.setQuantity(merged);
                    cart.addItem(item);
                } else {
                    existing.setQuantity(merged);
                }
            }
        }

        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
        return toResponse(cart);
    }

    /**
     * Xóa toàn bộ giỏ sau khi đặt hàng thành công. Gọi từ OrderService trong
     * cùng transaction để đơn và giỏ không lệch nhau.
     */
    @Transactional
    public void clearCartForOrder(Cart cart) {
        if (cart == null) {
            return;
        }
        cart.getItems().clear();
        cart.setUpdatedAt(LocalDateTime.now());
        this.cartRepository.save(cart);
    }

    // ===== Helper =====

    private void validateStock(Product product, long requestedQty) {
        if (requestedQty > product.getQuantity()) {
            throw new AppException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        }
    }

    /**
     * Chặn số lượng vượt trần mỗi khách của phiên flash (V16/BR-F17).
     *
     * <p>
     * Trước đây giỏ chỉ kiểm tồn kho nên khách đặt 3 máy trong khi phiên cho tối
     * đa 1/khách vẫn lưu được — dòng lặng lẽ rơi về giá thường rồi khách mới ngỡ
     * ngàng ở bước sau. Nay chặn thẳng ở thao tác giỏ.
     *
     * @param userId khách đang thao tác (cần để biết họ đã mua bao nhiêu)
     */
    private void enforceFlashPerUserLimit(String userId, Product product, long requestedQty) {
        Map<String, FlashPriceView> flash = this.flashSaleService.resolvePriceMap(
                List.of(product.getId()), userId, LocalDateTime.now());
        FlashPriceView view = flash.get(product.getId());
        if (view == null || view.allowsFlashFor(requestedQty)) {
            return; // không có phiên, hoặc số lượng vẫn nằm trong suất còn lại
        }
        Integer limit = view.perUserLimit();
        String detail = limit != null
                ? String.format("Bạn chỉ được mua tối đa %d máy/khách ở giá sốc của phiên này", limit)
                : "Bạn đã dùng hết suất giá sốc của phiên này";
        throw new AppException(ErrorCode.FLASH_PER_USER_LIMIT_EXCEEDED, detail);
    }

    /**
     * Số máy tối đa khách còn được thêm cho sản phẩm trong phiên flash, hoặc
     * {@code null} nếu sản phẩm không có phiên (không cần kẹp). Dùng cho luồng gộp
     * giỏ guest — ở đó ta KẸP thay vì ném lỗi để không làm hỏng cả thao tác merge.
     */
    private Long flashPerUserLimitLeft(String userId, Product product) {
        Map<String, FlashPriceView> flash = this.flashSaleService.resolvePriceMap(
                List.of(product.getId()), userId, LocalDateTime.now());
        FlashPriceView view = flash.get(product.getId());
        if (view == null) {
            return null;
        }
        Integer left = view.perUserLimitLeft();
        return left == null ? null : (long) left;
    }

    /** Tính phí ship theo chính sách hiện hành. subtotal = 0 → chưa tính ship. */
    public long calculateShippingFee(long subtotal) {
        if (subtotal <= 0) {
            return 0L;
        }
        return subtotal >= FREE_SHIPPING_THRESHOLD ? 0L : DEFAULT_SHIPPING_FEE;
    }

    public long getFreeShippingThreshold() {
        return FREE_SHIPPING_THRESHOLD;
    }

    /**
     * Chạy promotion trên giỏ hiện tại của khách, trả kèm dòng hàng để nơi gọi
     * tính tiếp voucher (D14/D22).
     *
     * <p>
     * Tách ra cho {@code OrderService.validateVoucher} tái dùng: preview ở trang
     * giỏ phải chạy ĐÚNG engine và ĐÚNG giá (kể cả giá flash) như lúc chốt đơn,
     * nếu không con số hiển thị lệch với số thực thu.
     */
    @Transactional(readOnly = true)
    public CartPricing priceCart(String userId) {
        Cart cart = this.cartRepository.findByUserId(userId).orElse(null);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return new CartPricing(List.of(), 0L, 0L, List.of());
        }
        List<CartItemResponse> items = cart.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .map(this::toItemResponse)
                .toList();
        applyFlashPrices(items, cart);

        long subtotal = items.stream().mapToLong(CartItemResponse::getLineTotal).sum();
        PromotionEngine.Result promo = this.promotionEngine.resolve(
                toEngineLines(items), this.promotionService.findApplicable(LocalDateTime.now()),
                LocalDateTime.now());
        return new CartPricing(items, subtotal, promo.promotionDiscount(),
                CartPricing.toEligibleLines(items, promo));
    }

    /**
     * Giỏ đã tính tiền, kèm dòng hàng ở dạng đủ để xét scope voucher (D22).
     *
     * @param linesForVoucher mỗi dòng kèm categoryId/factory + phần promotion đã giảm
     */
    public record CartPricing(List<CartItemResponse> items, long subtotal, long promotionDiscount,
            List<VoucherService.EligibleLine> linesForVoucher) {

        private static List<VoucherService.EligibleLine> toEligibleLines(List<CartItemResponse> items,
                PromotionEngine.Result promo) {
            Map<String, Long> discountByProduct = promo == null ? Map.of()
                    : promo.lines().stream().collect(Collectors.toMap(
                            PromotionEngine.LineResult::productId,
                            PromotionEngine.LineResult::discount, (a, b) -> a));
            return items.stream()
                    .map(i -> new VoucherService.EligibleLine(i.getProductId(), i.getCategoryId(),
                            i.getFactory(), i.getLineTotal(),
                            discountByProduct.getOrDefault(i.getProductId(), 0L)))
                    .toList();
        }
    }

    /** Map cart entity → response kèm phần tính tiền. cart = null → giỏ rỗng. */
    private CartResponse toResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setFreeShippingThreshold(FREE_SHIPPING_THRESHOLD);

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            response.setItems(List.of());
            response.setTotalItems(0L);
            response.setSubtotal(0L);
            response.setShippingFee(0L);
            response.setTotal(0L);
            return response;
        }

        List<CartItemResponse> items = cart.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .map(this::toItemResponse)
                .toList();

        // D25: dòng trong phiên flash dùng flashPrice làm giá bán, engine bỏ qua.
        applyFlashPrices(items, cart);

        long totalItems = items.stream().mapToLong(CartItemResponse::getQuantity).sum();
        long subtotal = items.stream().mapToLong(CartItemResponse::getLineTotal).sum();
        long shippingFee = calculateShippingFee(subtotal);

        // Preview khuyến mại (D14). Dùng CÙNG engine với lúc chốt đơn để con số
        // FE hiển thị không lệch với số thực thu.
        PromotionEngine.Result promo = promotionEngine.resolve(
                toEngineLines(items), promotionService.findApplicable(LocalDateTime.now()),
                LocalDateTime.now());

        applyPromotionToItems(items, promo);
        long promotionDiscount = promo.promotionDiscount();

        response.setId(cart.getId());
        response.setItems(items);
        response.setTotalItems(totalItems);
        response.setSubtotal(subtotal);
        response.setShippingFee(shippingFee);
        response.setTotal(subtotal + shippingFee);
        response.setPromotionDiscount(promotionDiscount);
        response.setPayable(Math.max(0L, subtotal + shippingFee - promotionDiscount));
        response.setPromotions(toAppliedPromotions(promo));
        return response;
    }

    /**
     * D25: dòng trong phiên flash lấy flashPrice làm giá bán. Gắn flashPrice vào
     * item rồi tính lại lineTotal theo giá flash.
     *
     * <p>
     * Phải hỏi theo ĐÚNG khách đang xem giỏ (không phải bản không-user) để
     * {@code perUserLimitLeft} có giá trị: preview phải khớp lúc chốt đơn (D14),
     * nếu không khách thấy giá flash ở giỏ rồi bị tính giá thường khi đặt.
     */
    private void applyFlashPrices(List<CartItemResponse> items, Cart cart) {
        String userId = cart.getUser() == null ? null : cart.getUser().getId();
        List<String> ids = items.stream().map(CartItemResponse::getProductId).toList();
        Map<String, FlashPriceView> flash = this.flashSaleService.resolvePriceMap(
                ids, userId, LocalDateTime.now());
        for (CartItemResponse item : items) {
            FlashPriceView view = flash.get(item.getProductId());
            // D32: hết suất của khách → dòng về giá thường (không chặn đơn).
            if (view == null) {
                continue;
            }
            // Ghi trần mỗi khách để FE nói rõ "tối đa N/khách" — kể cả khi đã hết
            // suất (flashPrice vẫn null nhưng FE cần con số trần để giải thích).
            item.setFlashPerUserLimit(view.perUserLimit());
            if (!view.allowsFlashFor(item.getQuantity())) {
                // Có phiên + có trần + khách đã dùng hết suất → cờ để FE báo chữ.
                item.setFlashLimitReached(true);
                continue;
            }
            item.setFlashPrice(view.flashPrice());
            item.setLineTotal(view.flashPrice() * item.getQuantity());
        }
    }

    /** Map dòng giỏ → dòng engine. flashPrice được nạp để engine bỏ qua dòng flash (D25). */
    private List<PromotionEngine.Line> toEngineLines(List<CartItemResponse> items) {
        return items.stream()
                .map(i -> new PromotionEngine.Line(i.getProductId(), i.getCategoryId(), i.getFactory(),
                        i.getPrice() == null ? 0L : i.getPrice(), (int) i.getQuantity(),
                        i.getFlashPrice()))
                .toList();
    }

    /** Ghi {@code lineDiscount} trả về từ engine vào từng dòng. */
    private void applyPromotionToItems(List<CartItemResponse> items, PromotionEngine.Result promo) {
        Map<String, Long> byProduct = promo.lines().stream()
                .collect(Collectors.toMap(PromotionEngine.LineResult::productId,
                        PromotionEngine.LineResult::discount, (a, b) -> a));
        items.forEach(i -> i.setLineDiscount(byProduct.getOrDefault(i.getProductId(), 0L)));
    }

    /** Chỉ giữ promotion thực sự giảm tiền — FE không hiện "✓" cho ưu đãi vô hiệu. */
    private List<AppliedPromotionResponse> toAppliedPromotions(PromotionEngine.Result promo) {
        Map<String, Long> discountByPromotion = promo.lines().stream()
                .filter(l -> l.promotion() != null && l.discount() > 0)
                .collect(Collectors.groupingBy(l -> l.promotion().getId(),
                        Collectors.summingLong(PromotionEngine.LineResult::discount)));

        return promo.appliedPromotions().stream()
                .filter(p -> discountByPromotion.getOrDefault(p.getId(), 0L) > 0)
                .map(p -> {
                    AppliedPromotionResponse res = new AppliedPromotionResponse();
                    res.setId(p.getId());
                    res.setName(p.getName());
                    res.setTitle(p.getTitle());
                    // Gửi kèm quy tắc để FE hiện đúng mệnh giá chương trình, không
                    // phải số tiền tính ra cho giỏ hiện tại.
                    res.setDiscountType(p.getDiscountType());
                    res.setDiscountValue(p.getDiscountValue());
                    res.setMaxDiscountAmount(p.getMaxDiscountAmount());
                    res.setMinOrderValue(p.getMinOrderValue());
                    res.setMinQuantity(p.getMinQuantity());
                    res.setUsageLimit(p.getUsageLimit());
                    res.setDiscountAmount(discountByPromotion.get(p.getId()));
                    return res;
                })
                .toList();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();
        CartItemResponse res = new CartItemResponse();
        res.setId(item.getId());
        res.setProductId(product.getId());
        res.setProductCode(product.getCode());
        res.setProductName(product.getName());
        res.setProductImage(product.getImage());
        res.setFactory(product.getFactory());
        res.setCategory(product.getCategory() != null ? product.getCategory().getName() : null);
        res.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        res.setPrice(product.getPrice());
        res.setOriginalPrice(product.getOriginalPrice());
        res.setQuantity(item.getQuantity());
        res.setLineTotal(product.getPrice() * item.getQuantity());
        res.setAvailableQuantity(product.getQuantity());
        return res;
    }
}
