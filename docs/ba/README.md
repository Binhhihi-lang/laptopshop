# Tài liệu nghiệp vụ (BA) — Module Khuyến mại & Ưu đãi

> Phiên bản: 1.0 · Cập nhật: 2026-09-27
> Nhánh: `feature/promotion-voucher` (BE) · `main` (FE)
> Đối tượng đọc: dev BE/FE, QA, người duyệt nghiệp vụ

Bộ tài liệu này mô tả **4 chức năng** được bổ sung vào LaptopShop:

| # | Chức năng | Tài liệu | Bản chất |
|---|---|---|---|
| 1 | **Voucher** | [01-voucher.md](01-voucher.md) | Phiếu giảm giá cấp ĐƠN — khách gõ mã hoặc chọn từ ví |
| 2 | **Promotion** | [02-promotion.md](02-promotion.md) | Chương trình giảm giá cấp DÒNG — tự áp, khách không chọn |
| 3 | **Flash Sale** | [03-flash-sale.md](03-flash-sale.md) | Phiên giá sốc theo giờ — đổi giá ngay trên thẻ sản phẩm |
| 4 | **Home Banner** | [04-banner.md](04-banner.md) | Slide carousel trang chủ — khối hiển thị, không đụng tiền |

> **Phần CHƯA LÀM** (14 hạng mục cho sprint sau) nằm ở [05-backlog.md](05-backlog.md) —
> gồm cả một **bug cần sửa sớm** (BL-05: `PromotionType` chưa bị chặn).

> **Tài liệu khác trong thư mục này:** [06-dashboard.md](06-dashboard.md) — Bảng điều khiển
> quản trị (số liệu thật, biểu đồ doanh thu, phân quyền Admin/Staff). Không thuộc module
> khuyến mại nhưng dùng chung quy ước tài liệu và ma trận phân quyền ở §7.

---

## 1. Bối cảnh & mục tiêu

Trước module này, hệ thống chỉ có **một cơ chế giảm giá duy nhất**: một mã giảm giá
công khai (`Coupon`) trừ thẳng vào tổng đơn, không giới hạn phạm vi, không thuộc về
ai, và trang chủ có hero **hardcode** trong `home.component.html` — muốn đổi nội dung
khuyến mại phải sửa code và deploy lại.

Module này giải 4 bài toán:

1. **Đa dạng hoá ưu đãi** — tách "giảm giá sản phẩm" (theo dòng) khỏi "phiếu giảm giá"
   (theo đơn), đúng cách các sàn TMĐT (Shopee/Tiki/Lazada) vận hành.
2. **Voucher thuộc về khách** — có ví voucher: khách tự nhận, admin tặng đích danh.
3. **Giá sốc theo phiên** — flash sale có đồng hồ đếm ngược và kho riêng, hiển thị
   ngay trên thẻ sản phẩm mà không cần sửa giá gốc.
4. **Trang chủ quản trị được** — admin tự thêm/sửa/ẩn slide banner, không cần deploy.

**Ngoài phạm vi (đã chốt, KHÔNG làm):**
- Điểm thưởng / loyalty point.
- Nhiều voucher trên cùng một đơn (v1: tối đa 1).
- Cộng dồn nhiều promotion trên cùng một dòng (v1: mỗi dòng 1 ưu đãi tốt nhất).
- Flash sale lặp lịch tự động hằng ngày (v1: admin tạo từng phiên thủ công).
- `PromotionType.GIFT_VOUCHER` (tặng voucher khi đơn hoàn tất) và `BUNDLE` (mua kèm).

---

## 2. Bốn chức năng khác nhau ở đâu?

Đây là bảng quan trọng nhất — đọc trước khi đọc chi tiết từng chức năng.

| Tiêu chí | **Promotion** | **Voucher** | **Flash Sale** | **Home Banner** |
|---|---|---|---|---|
| Ai khởi xướng | Admin tạo chương trình | Admin tạo mẫu → khách nhận/gõ mã | Admin tạo phiên + chọn SP | Admin tạo slide |
| Khách có chọn? | **Không** — tự áp | **Có** — gõ mã / chọn từ ví | **Không** — tự áp | Không (chỉ bấm xem) |
| Cấp giảm | **Từng dòng** sản phẩm | **Cấp đơn** | **Giá bán** từng dòng | Không giảm giá |
| Khách thấy giá ưu đãi ở đâu | Chỉ trong **giỏ/checkout** | Trong giỏ/checkout | **Ngay trên thẻ SP** ở home/list/detail | — |
| Thời gian | `startDate` → `endDate` (nhiều ngày) | `startDate`/`expiryDate` | **Phiên** `startAt` → `endAt` (vài giờ) | Không (bật/tắt thủ công) |
| Kho / hạn mức | `usageLimit` = ngân sách theo **đơn** | `usageLimit` + `perUserLimit` | `flashStock` + `soldInFlash` + `perUserLimit` | Tối đa **5 slide** bật cùng lúc |
| Thuộc về ai | Không — ai mua cũng hưởng | **Có** — ví `UserVoucher` gắn `user_id` | Không | Không |
| Ghi vào đơn | `OrderDetail.discountAmount` + `promotionId` | `Order.voucherDiscount` + `voucher_id` | Giá flash nằm trong `OrderDetail.price` | — |
| Ghi đè `Product.price`? | Không | Không | **Không bao giờ** | — |

**Điểm dễ nhầm nhất:** `Promotion.discountValue` kiểu `AMOUNT` giảm **mỗi máy**
(× số lượng), còn `Voucher.discountAmount` trừ **một lần** cho cả đơn. Xem §4.

---

## 3. Kiến trúc tổng thể

```
┌──────────────────────────── ADMIN (quản trị) ────────────────────────────┐
│  /api/v1/admin/vouchers        VoucherRestController  + VoucherService   │
│  /api/v1/admin/promotions      PromotionRestController + PromotionService│
│  /api/v1/admin/flash-sales     FlashSaleRestController + FlashSaleService│
│  /api/v1/admin/home-banners    HomeBannerRestController + HomeBannerService│
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────── CLIENT (storefront) ─────────────────────────┐
│  /api/v1/client/vouchers        ví, kho nhận được, validate mã           │
│  /api/v1/client/flash-sales     phiên đang chạy / sắp tới / chi tiết     │
│  /api/v1/client/home-banners    slide trang chủ (PUBLIC, không cần login)│
└──────────────────────────────────────────────────────────────────────────┘
                                   ↓ dùng chung
        ┌──────────────────────────────────────────────────────┐
        │  PromotionEngine.resolve()   ← HÀM THUẦN, không chạm DB │
        │  VoucherService.calculateEligibleAmount() / calculateDiscount() │
        │  FlashSaleService.resolvePriceMap()  ← nguồn giá flash  │
        └──────────────────────────────────────────────────────┘
                                   ↓ gọi từ
        ┌──────────────────────────────────────────────────────┐
        │  CartService.toResponse()      → PREVIEW (chưa chốt)   │
        │  OrderService.createOrder()    → CHỐT ĐƠN (ghi DB)     │
        └──────────────────────────────────────────────────────┘
```

**Nguyên tắc vàng:** preview ở giỏ và chốt đơn dùng **đúng một hàm**. Không bao giờ
viết lại logic tính tiền ở hai chỗ — nếu không, khách thấy một số ở giỏ và bị thu
một số khác khi đặt hàng.

---

## 4. Công thức tính tiền (thứ tự cứng)

Áp dụng cho một giỏ hàng, tại thời điểm chốt đơn:

```
Bước 1 — Giá bán từng dòng (flash thắng):
    linePrice  = flashPrice   nếu sản phẩm đang trong phiên còn suất
                              VÀ khách chưa hết suất của mình (perUserLimit)
                 product.price  nếu không
    lineTotal  = linePrice × quantity

Bước 2 — Tổng tiền hàng:
    subtotal   = Σ lineTotal

Bước 3 — Promotion (cấp DÒNG, chạy TRƯỚC):
    mỗi dòng chọn 1 promotion thắng (priority cao nhất; hoà thì giảm nhiều hơn)
    dòng đã có flashPrice → lineDiscount = 0 (flash thắng, D25)
    promotionDiscount = Σ lineDiscount,  cap bởi maxDiscountAmount từng chương trình

Bước 4 — Voucher (cấp ĐƠN, chạy SAU):
    lineNet        = lineTotal − lineDiscount
    eligibleAmount = scopeType=ALL ? (subtotal − promotionDiscount)
                                   : Σ lineNet của các dòng KHỚP phạm vi
    voucherDiscount = min( f(eligibleAmount), eligibleAmount, maxDiscountAmount )

Bước 5 — Chốt:
    discountAmount = min(promotionDiscount + voucherDiscount, subtotal)   ← không bao giờ âm
    shippingFee    = subtotal ≥ 2.000.000đ ? 0 : 50.000đ
    totalPrice     = subtotal − discountAmount + shippingFee
```

**Cách `f()` tính theo loại:**

| Loại | Cấp | Công thức | Ví dụ |
|---|---|---|---|
| `Promotion` `PERCENT` | dòng | `lineTotal × % / 100` | 20tr × 8% = 1.600.000đ |
| `Promotion` `AMOUNT` | **mỗi máy** | `discountValue × quantity` | 500k × 2 máy = **1.000.000đ** |
| `Voucher.discountPercent` | **đơn** | `eligibleAmount × % / 100`, cap `maxDiscountAmount` | 25tr × 5% = 1.250.000đ |
| `Voucher.discountAmount` | **đơn, 1 lần** | `discountAmount` | 300k mua 10 món vẫn 300k |

> **Vì sao `AMOUNT` của promotion là per-máy còn của voucher là per-đơn?** Các sàn
> ký hiệu ưu đãi trên **đơn giá** ("Trợ giá 500k", "-20% Flash Sale"), còn voucher là
> **một cái phiếu** trừ một lần ("Giảm 20k, đơn từ 99k"). Gộp cùng cấp sẽ hoặc làm
> khách phàn nàn ("mua 3 cái vẫn chỉ giảm 500k"), hoặc lỗ khi khách thêm số lượng.

**Flash sale không xuất hiện trong dòng "Giảm giá"** ở checkout — nó đã nằm trong
`linePrice` (giá bán) rồi. Khách thấy giá sốc ngay trên thẻ sản phẩm từ trang chủ.

### 4.1 Trừ hạn mức & tồn kho — LUÔN atomic (BR-A01…BR-A04)

Mọi thứ bị **giới hạn số lượng** đều phải trừ bằng **UPDATE có điều kiện ngay trong câu
lệnh**, không đọc-rồi-ghi. Lý do: hai request song song cùng đọc giá trị cũ rồi cùng ghi
→ **mất một lần trừ** (lost update).

| Mã | Đối tượng | Câu UPDATE | Ai làm |
|---|---|---|---|
| **BR-A01** | `Promotion.usedCount` | `SET usedCount = usedCount + 1 WHERE usageLimit IS NULL OR usedCount < usageLimit` | `PromotionRepository.incrementUsedCount` ✅ |
| **BR-A02** | `FlashSaleItem.soldInFlash` | `SET soldInFlash = soldInFlash + :qty WHERE soldInFlash + :qty <= flashStock` | `FlashSaleItemRepository.consumeStock` ✅ |
| **BR-A03** | `Voucher.usedCount` | `SET usedCount = COALESCE(usedCount,0) + 1 WHERE usageLimit IS NULL OR usageLimit <= 0 OR COALESCE(usedCount,0) < usageLimit` | `VoucherRepository.incrementUsedCount` ✅ |
| **BR-A04** | `Product.quantity` + `sold` | `SET quantity = quantity - :qty, sold = sold + :qty, updatedAt = NOW() WHERE id = :id AND quantity >= :qty` | `ProductRepository.deductStock` ✅ |
| **BR-A05** | Trường số khi tạo/sửa (validate) | Chặn giá trị vô nghĩa — xem §4.2 | `PromotionService` / `VoucherService` ✅ |

**Cách dùng:** service kiểm tra **số dòng bị ảnh hưởng**. `0` dòng = vừa bị người khác
giành mất → xử lý theo từng loại:

| Loại | Khi 0 dòng | Vì sao |
|---|---|---|
| Promotion | Ném `PROMOTION_OUT_OF_STOCK` → rollback cả đơn | Ngân sách chương trình, không thể vượt |
| Voucher | Ném `VOUCHER_OUT_OF_STOCK` → rollback cả đơn | Hạn mức voucher, không thể vượt |
| Flash sale | **Âm thầm về giá thường**, KHÔNG fail đơn (BR-F08) | Đây là *giá*, không phải *phiếu* — khách vẫn mua được hàng |
| Tồn kho SP | Ném `CART_QUANTITY_EXCEEDS_STOCK` → rollback | Không được bán quá hàng có |

> **Vì sao Promotion/Voucher phải ném lỗi mà Flash sale thì không?** Promotion/Voucher là
> **cam kết giảm giá**: nếu âm thầm bỏ đi thì khách bị thu nhiều tiền hơn kỳ vọng lúc bấm
> "Đặt hàng". Flash sale chỉ là **giá bán**: hết suất thì mua giá thường vẫn hợp lý, khách
> không bị mất tiền oan. Hai tình huống khác bản chất nên xử lý khác nhau — đều là quyết
> định có chủ ý.

> **Bẫy `updatedAt`:** `Product.updatedAt` là `@LastModifiedDate` do Hibernate ghi khi
> `save()`. Nếu chuyển sang UPDATE atomic thì **Hibernate không tự cập nhật `updatedAt`**
> → phải set `updatedAt = NOW()` ngay trong câu UPDATE, nếu không cột "Ngày sửa" ở màn
> admin sản phẩm sẽ đứng yên dù kho đã đổi.

> **Bẫy JPQL bulk update:** `@Modifying` UPDATE **không đi qua persistence context** →
> entity đã load trong transaction vẫn giữ giá trị cũ. Test đọc lại ngay sau đó sẽ thấy
> số cũ (phải `flush()` + `clear()` trước khi assert). Trong luồng sản phẩm điều này
> không gây hại vì `createOrder` **không đọc lại** `product.quantity` sau khi trừ.

### 4.2 `0` KHÁC nghĩa giữa Voucher và Promotion (BR-A05)

Cùng tên trường nhưng `0` mang nghĩa **ngược nhau** — dễ gây bug im lặng:

| Trường | `0` nghĩa là | Chặn khi tạo |
|---|---|---|
| `Voucher.usageLimit` | **Không giới hạn** (`isVoucherUsable` chỉ chặn khi `> 0`) | chỉ chặn `< 0` |
| `Voucher.perUserLimit` | **Không giới hạn** (`hasReachedPerUserLimit` chỉ chặn khi `> 0`) | chỉ chặn `< 0` |
| `Promotion.usageLimit` | **Không bao giờ áp** (`hasBudget()` → `usedCount < 0` luôn false) | chặn `<= 0` |
| `Promotion.maxDiscountAmount` / `Voucher.maxDiscountAmount` | Không có trần (engine bỏ qua khi `<= 0`) | chỉ chặn `< 0` |
| `minOrderValue` / `minQuantity` | Không yêu cầu | chỉ chặn `< 0` |

> **Vì sao `Promotion.usageLimit = 0` bị chặn mà `Voucher.usageLimit = 0` thì không?**
> Vì code xử lý khác nhau: voucher kiểm `usageLimit > 0` trước khi so `usedCount`, còn
> promotion dùng `hasBudget()` = `usedCount < usageLimit` — với `usageLimit = 0` thì
> `0 < 0` luôn sai → chương trình **chết im lặng**. Đây là **bất đối xứng có thật trong
> code**, không phải thiết kế; đã chốt giữ nguyên hành vi và chặn ở tầng validate.
>
> **UI:** form voucher ghi rõ *"Đặt 0 nếu không giới hạn"* cho "Tổng lượt dùng" → giá trị
> mặc định là `0`. Form promotion để `null` + `Validators.min(1)`.

---

## 5. Vòng đời

### 5.1 Voucher

```
Voucher (mẫu, admin tạo; voucherType = PUBLIC | ASSIGNED | GIFT)
   │  khách CLAIM (bấm "Lưu mã")  hoặc  admin ASSIGN (tặng đích danh)
   ↓
UserVoucher (ví của khách)   AVAILABLE
   │  chọn ở checkout
   ↓
   USED (gắn order_id)
   │
   ├─ hủy đơn  → về AVAILABLE nếu còn hạn, ngược lại EXPIRED   (D12)
   └─ quá hạn (job) → EXPIRED
```

### 5.2 Promotion

```
Tạo (admin) → active + startDate..endDate + còn ngân sách
   → engine khớp scope + loại trừ + minQuantity/minOrderValue
   → chốt đơn: usedCount +1 cho MỖI chương trình đã áp (1 lần/ĐƠN, không theo dòng)
   → hủy đơn: usedCount −1
```

### 5.3 Flash Sale

```
Admin tạo phiên (startAt..endAt) + thêm sản phẩm (flashPrice < giá thường, flashStock)
   → trong khung giờ + active=true: resolvePriceMap trả giá flash → card/giỏ/đơn
   → chốt đơn: soldInFlash + qty (atomic)
   → hết phiên / hết kho / tắt công tắc → tự về giá thường (KHÔNG sửa Product.price)
   → hủy đơn: soldInFlash − qty
```

---

## 6. Từ điển thuật ngữ

| Thuật ngữ | Nghĩa | Ghi chú |
|---|---|---|
| **Voucher** | Phiếu giảm giá. Hai tầng: `Voucher` (mẫu admin tạo) và `UserVoucher` (bản trong ví khách) | UI chỉ dùng chữ "Voucher"; giữ chữ "mã" khi nói về code khách gõ |
| **Promotion** | Chương trình khuyến mại tự áp, giảm theo dòng sản phẩm | UI gọi là "Khuyến mại" |
| **Flash Sale** | Phiên giá sốc theo giờ, kho riêng | UI gọi "Flash Sale" / "Giá sốc" |
| **Home Banner** | Slide carousel trang chủ | UI gọi "Banner trang chủ" |
| **eligibleAmount** | Tiền hàng **đủ điều kiện** áp voucher: chỉ tính các dòng khớp phạm vi, đã trừ promotion của dòng | Dùng cho CẢ `minOrderValue` lẫn base tính giảm |
| **lineDiscount** | Tiền promotion giảm riêng một dòng | Snapshot vào `OrderDetail.discountAmount` |
| **promotionDiscount** | Tổng giảm từ promotion của cả đơn | `Order.promotionDiscount` |
| **voucherDiscount** | Tiền giảm từ voucher của cả đơn | `Order.voucherDiscount` |
| **discountAmount** | Tổng giảm = promotion + voucher | `Order.discountAmount` (giữ để tương thích đơn cũ) |
| **Phạm vi (scope)** | Voucher/Promotion áp cho nhóm nào: `ALL` / `CATEGORY` / `BRAND` / `PRODUCT` | `BRAND` so khớp `Product.factory` |
| **Loại trừ (exclude)** | Sản phẩm bị chừa ra khỏi phạm vi promotion | Loại trừ **thắng** phạm vi |
| **Nguồn (source)** | Voucher vào ví bằng đường nào: `CLAIMED` / `GIFTED` / `WELCOME` / `BIRTHDAY` | |
| **flashStock / soldInFlash** | Kho riêng của phiên / số đã bán trong phiên | Không đụng `Product.quantity` |

---

## 7. Ma trận phân quyền

Theo mô hình vai trò của dự án: **ADMIN** toàn quyền · **STAFF** CRUD danh mục &
đơn nhưng **KHÔNG xoá** · **CUSTOMER** chỉ storefront.

| Permission | ADMIN | STAFF | CUSTOMER |
|---|:--:|:--:|:--:|
| `CREATE_VOUCHER` / `READ` / `UPDATE` / `DELETE_VOUCHER` | ✅ | ✅ (trừ DELETE) | ❌ |
| `CREATE_PROMOTION` / `READ` / `UPDATE_PROMOTION` | ✅ | ✅ | ❌ |
| `DELETE_PROMOTION` | — **không tồn tại** | — | — |
| `CREATE_FLASH_SALE` / `READ` / `UPDATE` / `DELETE_FLASH_SALE` | ✅ | ✅ (trừ DELETE) | ❌ |
| `CREATE_HOME_BANNER` / `READ` / `UPDATE` / `DELETE_HOME_BANNER` | ✅ | ✅ (trừ DELETE) | ❌ |

**Lưu ý về Promotion:** không có quyền `DELETE_PROMOTION`. Endpoint `DELETE
/api/v1/admin/promotions/{id}` dùng quyền `UPDATE_PROMOTION` và thực chất là **ngừng
áp** (soft), vì chương trình đã áp lên đơn cũ phải giữ lại để tra cứu.

---

## 8. Giải thích chi tiết các khái niệm lõi

> Phần này viết cho người **đọc lần đầu** — giải thích "vì sao" đằng sau §4, kèm **bảng
> đối chiếu** sang [01-voucher.md](01-voucher.md) và [02-promotion.md](02-promotion.md)
> để tra cứu chéo.

### 8.1 "Promotion Engine" là gì

Là **một hàm thuần** trong `PromotionEngine.java`:

```java
Result resolve(List<Line> lines, List<Promotion> promotions, LocalDateTime now)
```

| | |
|---|---|
| **Input** | danh sách dòng hàng + danh sách chương trình + mốc thời gian |
| **Output** | `Result { subtotal, promotionDiscount, lines[] }` — mỗi dòng kèm `discount` + chương trình nào đã thắng |

**"Thuần" nghĩa là:** không chạm DB, không tự đọc `LocalDateTime.now()`, không gọi service
khác. Mọi thứ nó cần đều là **tham số truyền vào**.

**Vì sao phải thuần?** Có **hai chỗ** cần biết tiền giảm:

| Chỗ | Mục đích |
|---|---|
| `CartService.toResponse()` | **Preview** — khách xem giỏ thấy "Giảm giá sản phẩm −2.559.200" |
| `OrderService.createOrder()` | **Chốt đơn** — ghi con số thật vào DB |

Nếu viết hai lần thì kiểu gì cũng lệch (sửa chỗ này quên chỗ kia). Nên cả hai gọi **đúng
một hàm**. Vì thuần nên test được toàn bộ ma trận quyết định mà không cần Spring context.

### 8.2 "Mỗi dòng chọn 1 promotion thắng" — tiêu chí đánh giá

Chia làm **4 giai đoạn**. Hãy tưởng tượng mỗi dòng hàng đi qua một cuộc thi.

**Giai đoạn 1 — Vòng loại.** Với **một dòng hàng**, hàm `matches()` lọc theo thứ tự,
**tất cả** phải đúng:

| # | Điều kiện | Ghi chú |
|---|---|---|
| 1 | `active = true` | |
| 2 | `startDate ≤ now ≤ endDate` | |
| 3 | Còn ngân sách (`usedCount < usageLimit`) | Ngân sách đếm theo **đơn** |
| 4 | `line.quantity ≥ minQuantity` | Số lượng **của riêng dòng đó** |
| 5 | `subtotal ≥ minOrderValue` | ⚠️ `subtotal` = tổng **CẢ GIỎ**, không phải dòng |
| 6 | Khớp phạm vi (scope) | ALL / CATEGORY / BRAND / PRODUCT |
| 7 | **Không** nằm trong danh sách loại trừ | Loại trừ **thắng** scope |
| 8 | Mức giảm tính ra > 0 | Chương trình giảm 0đ không được coi là thắng |
| 9 | Dòng **không** có `flashPrice` | Dòng flash bị loại thẳng — xem giai đoạn 2 |

**Giai đoạn 2 — Dòng có giá flash bị loại thẳng, không dự thi:**

```java
Promotion winner = line.hasFlash() ? null : pickWinner(line, candidates, subtotal, now);
```

Flash sale là giá sâu nhất rồi. Cộng thêm % promotion lên giá flash → lỗ. Nên dòng flash
có `lineDiscount = 0` luôn.

**Giai đoạn 3 — Chung kết: tiêu chí "thắng".** Trong số chương trình vượt vòng loại,
chọn **một** theo thứ tự ưu tiên:

```
1. priority cao hơn      →  thắng
2. hoà priority          →  mức giảm (đã cap theo dòng) lớn hơn  →  thắng
3. vẫn hoà               →  giữ cái gặp trước
```

`priority` giống "hạng" admin gán cho chương trình. Không gán gì → mặc định `0` → mọi
chương trình hoà nhau và **cái nào giảm nhiều hơn sẽ thắng** (có lợi cho khách).

Sau khi có winner: `lineDiscount = min( rawDiscount(winner, line), lineTotal )` — cap ở
`lineTotal` để không bao giờ giảm quá giá trị dòng.

**Giai đoạn 4 — Cap theo chương trình (dễ bỏ sót).** Cap **không** áp lẻ từng dòng, mà áp
trên **tổng các dòng mà một chương trình đã thắng**:

```
nhóm các dòng theo promotionId
với mỗi chương trình: nếu Σ lineDiscount > maxDiscountAmount
    → cắt phần dư, cắt dần theo thứ tự các dòng
```

VD: chương trình "500k/máy, cap 1tr" thắng 5 dòng × 500k = 2.5tr > cap 1tr → cắt còn 1tr.

> **Hai điểm quan trọng về "thắng":**
> - **Thắng theo TỪNG DÒNG, không theo đơn.** Dòng A có thể do chương trình X thắng, dòng B
>   do chương trình Y thắng. Đó là lý do `usedCount` phải tăng cho **từng chương trình distinct**.
> - **Một chương trình thắng 3 dòng thì `usedCount` chỉ +1** cho cả đơn (ngân sách đếm theo đơn).

### 8.3 `eligibleAmount` là gì

**Định nghĩa:** "tiền hàng **đủ điều kiện** để voucher được phép tác động".

```
lineNet        = lineTotal − lineDiscount        (tiền còn lại của từng dòng, sau promotion)
eligibleAmount = scopeType=ALL ? (subtotal − promotionDiscount)
                               : Σ lineNet của các dòng KHỚP phạm vi voucher
```

Nói cách khác: **chỉ tính tiền của những dòng mà voucher được phép giảm, và trừ đi phần
promotion đã giảm trên chính dòng đó.**

**Vì sao cần đại lượng này?** Vì nó dùng cho **HAI** việc cùng lúc:

**(a) Xét điều kiện `minOrderValue`**

Voucher khai: *"Chỉ áp cho Laptop văn phòng, đơn từ 20 triệu"*.
Giỏ hàng: 40 triệu, nhưng **chỉ 10 triệu là laptop văn phòng**.

- Nếu xét trên cả giỏ (40tr ≥ 20tr) → **cho áp** — SAI, vì phần laptop chỉ 10tr.
- Xét trên `eligibleAmount` (10tr < 20tr) → **chặn**. Đúng ý admin.

**(b) Tính số tiền giảm**

Cùng ví dụ, giả sử giỏ có 25tr laptop văn phòng + 15tr phụ kiện:

- Nếu giảm 10% trên **cả giỏ**: 10% × 40tr = **4 triệu**
- Đúng ra: 10% × 25tr = **2.5 triệu**

Lỗ hổng tệ hơn: giỏ 5tr laptop + 40tr phụ kiện = 45tr → 10% × 45tr = 4.5tr, gần bằng cả
giá trị laptop, vô lý.

> **Đây chính là lý do `eligibleAmount` ra đời (D22):** xét điều kiện trên tiền khớp scope
> mà cho giảm trên cả giỏ là lỗ hổng — khách chỉ cần thêm 1 món 200k khớp scope là được
> giảm 10% cả đơn.

**Vì sao `scopeType=ALL` lại trừ `promotionDiscount`?** Vì voucher chạy **sau** promotion
(D9) — không cho voucher tính % trên phần tiền đã được giảm rồi. Bảo vệ biên lợi nhuận,
đúng chuẩn Shopee/Tiki.

> **Lưu ý:** voucher khai `scopeType != ALL` nhưng **chưa chọn giá trị nào** →
> `matchesScope` trả `false` cho mọi dòng → `eligibleAmount = 0` → voucher không áp được.
> Đây là **cố ý**: trả `true` sẽ biến voucher "chỉ danh mục X" thành "áp cả đơn".

### 8.4 Voucher không hợp lệ với đơn → xử lý thế nào

**Không áp dụng.** Nhưng cách thể hiện **khác nhau ở hai đường** — đây là điểm quan trọng.

| | **Preview ở giỏ** (`POST /client/vouchers/validate`) | **Chốt đơn** (`POST /client/orders`) |
|---|---|---|
| HTTP | **Luôn 200** | Lỗi 400 |
| Trả về | `{valid: false, discountAmount: 0, message}` | Ném `AppException` |
| Khách thấy | Message inline dưới ô nhập mã | Toast lỗi, **đơn không được tạo** |
| Giỏ hàng | Giữ nguyên | Transaction rollback, giỏ còn nguyên |

**Vì sao preview phải trả 200 kể cả mã sai?** Để FE hiện thông báo nhẹ nhàng dưới ô nhập
mã, chứ không phải bật lỗi đỏ. Mã sai là chuyện thường, không phải sự cố hệ thống.

**Các nhánh bị chặn** (hàm `checkVoucherRules` — dùng chung cho **cả hai** đường):

| # | Nhánh vi phạm | ErrorCode | Message hiện cho khách |
|---|---|---|---|
| — | Mã để trống | `VOUCHER_CODE_EMPTY` (4019) | Vui lòng nhập mã voucher |
| — | Mã không tồn tại | `VOUCHER_NOT_FOUND` (4001) | Không tìm thấy voucher |
| 1a | Voucher bị khoá | `VOUCHER_INACTIVE` (4015) | Voucher đã bị khoá hoặc ngừng áp dụng |
| 1b | Voucher hết hạn | `VOUCHER_EXPIRED` (4003) | Voucher đã hết hạn sử dụng |
| 1c | Voucher hết lượt | `VOUCHER_OUT_OF_STOCK` (4004) | Voucher đã hết lượt sử dụng |
| 2 | `now < startDate` | `VOUCHER_NOT_STARTED` (4013) | Voucher chưa đến thời gian sử dụng |
| 3 | Chạm `perUserLimit` | `VOUCHER_PER_USER_LIMIT_REACHED` (4012) | Bạn đã dùng hết số lượt cho phép của voucher này |
| 4 | `eligibleAmount ≤ 0` | `VOUCHER_NO_ELIGIBLE_ITEM` (4017) | Voucher không áp dụng cho sản phẩm nào trong đơn |
| 5 | `eligibleAmount < minOrderValue` | `VOUCHER_MIN_ORDER_NOT_MET` (4011) | Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này |
| — | Giảm ra ≤ 0 | `VOUCHER_NO_DISCOUNT` (4018) | Voucher không tạo ra khoản giảm nào cho đơn này |

> **Cập nhật 2026-09-28:** trước đây nhánh 1 gộp thành một message "Voucher đã hết hạn hoặc
> hết lượt sử dụng", và ở **bước chốt đơn** message bị **vứt đi** — khách chỉ nhận
> `"Voucher không hợp lệ hoặc đã hết hạn"` chung chung, **không biết lý do thật**, dù preview
> đã nói rõ. Nay cả hai đường trả **cùng một ErrorCode chi tiết**. Chi tiết: [05-backlog.md](05-backlog.md) BL-02.

**Vì sao ở chốt đơn phải CHẶN, không được âm thầm bỏ qua?** Vì lúc này khách **đã bấm
"Đặt hàng"** với kỳ vọng được giảm. Nếu hệ thống lặng lẽ bỏ voucher rồi tạo đơn giá gốc →
**khách bị thu nhiều tiền hơn họ tưởng**. Đó là lỗi nghiêm trọng nhất trong luồng thanh toán.

Nguyên tắc: **hoặc áp đúng như preview, hoặc báo lỗi rõ ràng — không bao giờ có vùng xám.**

### 8.5 Ví dụ số chạy xuyên suốt (số trong mockup, đã verify)

```
Giỏ:  ASUS ROG     flash 27.990.000  × 1
      Dell XPS     flash 29.990.000  × 1
      HP Spectre   thường 31.990.000 × 1, khớp promotion −8%
```

**Bước 1–2 — giá và subtotal:**

```
lineTotal(ASUS) = 27.990.000   (giá flash)
lineTotal(Dell) = 29.990.000   (giá flash)
lineTotal(HP)   = 31.990.000
subtotal        = 89.970.000
```

**Bước 3 — promotion (từng dòng):**

```
ASUS → có flashPrice → winner = null      → lineDiscount = 0
Dell → có flashPrice → winner = null      → lineDiscount = 0
HP   → không flash   → winner = promo 8%  → lineDiscount = 31.990.000 × 8% = 2.559.200

promotionDiscount = 2.559.200
```

**Bước 4 — voucher (scope ALL, mã LAPTOP500K giảm 500k, đơn từ 20tr):**

```
lineNet(ASUS) = 27.990.000 − 0         = 27.990.000
lineNet(Dell) = 29.990.000 − 0         = 29.990.000
lineNet(HP)   = 31.990.000 − 2.559.200 = 29.430.800

eligibleAmount = 89.970.000 − 2.559.200 = 87.410.800
   (scope ALL → lấy subtotal trừ promotionDiscount, không cần cộng từng dòng)

Kiểm tra: 87.410.800 ≥ 20.000.000 ✓
voucherDiscount = min(500.000, 87.410.800) = 500.000
```

**Bước 5 — chốt:**

```
discountAmount = min(2.559.200 + 500.000, 89.970.000) = 3.059.200
shippingFee    = 0            (subtotal ≥ 2.000.000)
totalPrice     = 89.970.000 − 3.059.200 + 0 = 86.910.800
```

> Chú ý: **hai dòng flash không hề xuất hiện** trong `promotionDiscount` — giá sốc đã nằm
> sẵn trong `lineTotal` rồi. Đó là lý do dòng "Giảm giá" ở checkout chỉ chứa phần giảm
> **cộng thêm**.

### 8.6 Ba điểm dễ nhầm nhất

**① `minOrderValue` của promotion ≠ của voucher**

| | Xét trên |
|---|---|
| `Promotion.minOrderValue` | `subtotal` — **cả giỏ** |
| `Voucher.minOrderValue` | `eligibleAmount` — chỉ tiền **khớp phạm vi** |

Không nhất quán là **cố ý**: promotion giảm theo dòng nên "đơn tối thiểu" hiểu là đơn hàng
thật; voucher có phạm vi nên phải xét trên phần tiền nó được phép chạm.

**② `maxDiscountAmount` áp khác nhau cho 2 loại voucher**

```
Voucher số tiền (500k)      →  không cần trần (bản thân đã là con số chặn sẵn)
Voucher phần trăm (10%)     →  min(eligible × 10%, maxDiscountAmount)
```

Nên công thức chung `min(f(eligible), eligible, maxDiscountAmount)` ở §4 là **dạng rút
gọn** — nhánh số tiền không đi qua `maxDiscountAmount`.

**③ "Thắng" là theo từng dòng, không phải theo đơn**

Một đơn có thể có nhiều chương trình cùng hoạt động (mỗi chương trình thắng ở các dòng
khác nhau). Nhưng `usedCount` của mỗi chương trình chỉ tăng **1 lần cho cả đơn**.

### 8.7 Bảng đối chiếu — đọc chi tiết ở đâu

| Khái niệm trong §8 | Tài liệu chức năng | Mục cụ thể |
|---|---|---|
| Engine là hàm thuần | [02-promotion.md](02-promotion.md) | BR-P01, BR-P02 |
| Vòng loại (9 điều kiện) | [02-promotion.md](02-promotion.md) | §4.1 |
| Khớp phạm vi (scope) | [02-promotion.md](02-promotion.md) | §4.2 · BR-P08 (BRAND normalize) |
| Tiêu chí "thắng" (priority) | [02-promotion.md](02-promotion.md) | §4.3 · BR-P09 |
| Cap theo từng chương trình | [02-promotion.md](02-promotion.md) | §4.6 · BR-P12 |
| Flash thắng promotion | [02-promotion.md](02-promotion.md) · [03-flash-sale.md](03-flash-sale.md) | BR-P10 · BR-F09 |
| Cách tính mức giảm theo loại | [02-promotion.md](02-promotion.md) | §4.5 · BR-P11 |
| `eligibleAmount` (định nghĩa + 2 công dụng) | [01-voucher.md](01-voucher.md) | BR-V05 |
| `eligibleAmount` khi scope rỗng | [01-voucher.md](01-voucher.md) | BR-V06 · E-V01 |
| `perUserLimit` đếm 2 nguồn | [01-voucher.md](01-voucher.md) | BR-V07 |
| Trần voucher % (`maxDiscountAmount`) | [01-voucher.md](01-voucher.md) | BR-V08 · BR-V12 |
| Nhánh chặn voucher + ErrorCode | [01-voucher.md](01-voucher.md) | §4 BR-V04 · §7.3 bảng mã lỗi |
| Preview vs chốt đơn (2 đường) | [01-voucher.md](01-voucher.md) | UC-V07 vs UC-V08 · AC-V13 |
| Tăng `usedCount` atomic | [02-promotion.md](02-promotion.md) | BR-P03 · BR-P04 |
| Hoàn khi huỷ đơn | [01-voucher.md](01-voucher.md) | UC-V09 · BR-V09 |

---

## 9. Trạng thái triển khai (tại 2026-09-28)

| Hạng mục | Trạng thái |
|---|---|
| Backend — domain, engine, service, API, migration V2–V10 | ✅ đã có trên nhánh (chưa commit) |
| Frontend — trang admin + client, shared components | ✅ đã có trên nhánh (chưa commit) |
| Migration DB | ✅ V1→V10, đã verify trên MySQL 8 thật |
| Seed dữ liệu demo | ✅ `db/seed/demo-promotion-data.sql` |
| **ErrorCode chi tiết cho từng nhánh voucher** | ✅ **đã làm 2026-09-28** — 4 mã mới (4015/4017/4018/4019), tái dùng 4003/4004 |
| **BL-05 chặn `PromotionType` chưa hỗ trợ** | ✅ **đã xử lý 2026-09-28** — cách tốt hơn: **xoá hẳn** 2 giá trị enum thay vì validate |
| **BL-01 job dọn voucher hết hạn** | ✅ **đã làm 2026-09-28** — `VoucherExpiryJob`, cron mỗi giờ |
| **BL-02 dọn mã lỗi chết** | ✅ **đã xong** — xoá 7 mã (4014/4107/4109/4110/4112/4113/4309); còn 4010, 4310 |
| **Dọn enum không dùng** | ✅ **đã làm 2026-09-28** — xoá `GIFT_VOUCHER`/`BUNDLE`/`FIXED_PRICE`/`QUANTITY_TIER`/`VoucherType.GIFT`; migration **V11 + V12**; đồng bộ FE + mockup |
| **BL-15/16/17/18/19/20 — 6 bug rà soát** | ✅ **đã sửa hết 2026-09-29** — FE không tự tính tiền voucher; tính lại tiền khi flash cạn kho; atomic trừ kho + `usedCount`; validate dấu số; gộp query flash |
| Verify | ✅ BE **239 test pass** · FE `ng build` 0 error |

**Các hạng mục CHƯA làm** — chi tiết đầy đủ ở [05-backlog.md](05-backlog.md):

| Nhóm | Hạng mục | Ưu tiên |
|---|---|:--:|
| Vận hành | BL-03 flash lặp lịch hằng ngày | 🟡 |
| Promotion | BL-06 `GIFT_VOUCHER` · BL-07 `BUNDLE` · BL-08 `FIXED_PRICE` · BL-09 `QUANTITY_TIER` · BL-10 cộng dồn | 🟡🟡🟡🟢🟢 |
| Voucher | BL-11 nhiều voucher/đơn · BL-12 `WELCOME`/`BIRTHDAY` | 🟢🟢 |
| Trải nghiệm | BL-04 nhắc phiên sắp mở · BL-13 nhắc voucher hết hạn · BL-14 báo cáo | 🟢🟢🟢 |

> **Hoãn theo yêu cầu (2026-09-28):** BL-04 & BL-13 (liên quan chức năng **thông báo** —
> làm sau khi học phần đó), BL-14 (để dành khi làm **dashboard**). BL-12 và 4 hạng mục
> lớn (BL-07/09/10/11) **không mở rộng** — giữ nguyên trong backlog.

---

## 10. Quy ước đọc tài liệu

Mỗi tài liệu chức năng theo cấu trúc:

1. **Mục tiêu & phạm vi** — làm gì, không làm gì
2. **Actor** — ai dùng
3. **Use Case** — mã UC, luồng chính, luồng phụ
4. **Quy tắc nghiệp vụ** — mã BR, điều kiện, ngoại lệ
5. **Luồng nghiệp vụ** — sơ đồ
6. **Data Dictionary** — bảng, cột, kiểu, ý nghĩa
7. **API Contract** — endpoint, request, response, lỗi
8. **Màn hình & tương tác** — màn admin + client
9. **Edge case** — tình huống biên & cách xử lý
10. **Acceptance Criteria** — mã AC, điều kiện nghiệm thu
11. **Truy vết** — bảng đối chiếu BR → UC → AC → code

Mã tham chiếu dùng trong tài liệu: `BR-xx` (business rule), `UC-xx` (use case),
`AC-xx` (acceptance criteria), `D-xx` (quyết định thiết kế gốc).
