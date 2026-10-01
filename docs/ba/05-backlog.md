# 05 — BACKLOG (Các hạng mục CHƯA LÀM)

> Tài liệu nghiệp vụ · v1.0 · 2026-09-27
> Đọc kèm: [README.md](README.md) · các tài liệu 01–04 mô tả phần **đã có**
>
> **Trạng thái:** tài liệu này mô tả phần **chưa triển khai** — dùng làm đầu vào cho
> sprint tiếp theo. Mỗi hạng mục ghi rõ: giá trị nghiệp vụ, phạm vi thay đổi, quy tắc
> dự kiến, tiêu chí nghiệm thu dự kiến, và phụ thuộc.

---

## 0. Tổng quan mức ưu tiên

| Nhóm | Hạng mục | Ưu tiên | Quy mô |
|---|---|:--:|---|
| **A. Vận hành** | BL-01 Job hết hạn voucher | 🔴 Cao | Nhỏ |
| | BL-02 Làm sạch mã lỗi chết *(đã làm 1 phần 2026-09-28)* | 🟡 Trung bình | Nhỏ |
| | BL-03 Flash sale lặp lịch hằng ngày | 🟡 Trung bình | Vừa |
| | BL-04 Nhắc tôi khi phiên sắp mở | 🟢 Thấp | Vừa |
| **B. Promotion nâng cao** | BL-05 Chặn `PromotionType` chưa hỗ trợ ⚠️ **bug** | 🔴 Cao | Nhỏ |
| | BL-06 `GIFT_VOUCHER` — tặng voucher khi đơn hoàn tất *(phải thêm lại enum)* | 🟡 Trung bình | Vừa |
| | BL-07 `BUNDLE` — mua kèm giá ưu đãi *(phải thêm lại enum)* | 🟡 Trung bình | Lớn |
| | BL-08 `FIXED_PRICE` — ép giá bán cố định *(phải thêm lại enum)* | 🟡 Trung bình | Vừa |
| | BL-09 `QUANTITY_TIER` — giảm theo bậc số lượng *(phải thêm lại enum)* | 🟢 Thấp | Vừa |
| | BL-10 Cộng dồn nhiều promotion (`stackable`) | 🟢 Thấp | Lớn |
| **C. Voucher nâng cao** | BL-11 Nhiều voucher trên một đơn | 🟢 Thấp | Lớn |
| | BL-12 Voucher `WELCOME` / `BIRTHDAY` | 🟢 Thấp | Vừa |
| **D. Trải nghiệm** | BL-13 Nhắc voucher sắp hết hạn | 🟢 Thấp | Nhỏ |
| | BL-14 Báo cáo hiệu quả khuyến mại | 🟢 Thấp | Vừa |
| **E. Bug rà soát 2026-09-28** | BL-15 FE tự tính voucher trên `subtotal` ✅ | 🔴 Cao | Vừa |
| | BL-16 Fallback hết kho flash để lại `promotionDiscount` mồ côi ✅ | 🔴 Cao | Nhỏ |
| | BL-17 `Product.quantity` không có optimistic lock ✅ | 🟡 Trung bình | Nhỏ |
| | BL-18 `Voucher.usedCount` không atomic ✅ | 🟡 Trung bình | Nhỏ |
| | BL-19 Thiếu validate dấu trường số ✅ | 🟡 Trung bình | Nhỏ |
| | BL-20 N+1 trong `perUserLimit` flash sale ✅ | 🟢 Thấp | Nhỏ |

---

## NHÓM A — VẬN HÀNH

### BL-01 — Job dọn voucher hết hạn

**Trạng thái: ✅ ĐÃ LÀM (2026-09-28)** — tạo `VoucherExpiryJob.java`, chạy cron mỗi giờ
(`0 0 * * * *`), gọi `VoucherWalletService.expireOverdue()`. Cùng nhịp với
`PaymentCleanupJob`.

**Vấn đề gốc:** hàm `VoucherWalletService.expireOverdue()` **đã có sẵn** nhưng
**chưa được gắn `@Scheduled`** → không có gì gọi nó. Voucher quá hạn vẫn nằm ở trạng thái
`AVAILABLE` trong ví.

**Hệ quả nghiệp vụ:** khách mở ví thấy voucher ghi "Khả dụng" nhưng khi dùng thì bị chặn
→ trải nghiệm xấu, khách phàn nàn. (Lưu ý: luồng chốt đơn **vẫn an toàn** vì
`getUsableVoucher` kiểm tra hạn thật, không tin `status`.)

**Quy tắc đã áp dụng:**

| Mã | Quy tắc |
|---|---|
| BR-BL01a | Chạy **mỗi giờ** (cron giống `PaymentCleanupJob`) |
| BR-BL01b | Chỉ chuyển `AVAILABLE` → `EXPIRED` khi `expiresAt < now` |
| BR-BL01c | Voucher có `expiresAt = null` (trường tồn) **không bao giờ** bị chuyển |
| BR-BL01d | Job **không** đụng voucher đã `USED` |

**Tiêu chí nghiệm thu:** `expireOverdue` đã có test sẵn (`VoucherWalletServiceTest`)
→ chỉ cần job gọi đúng hàm, đã verify qua build.

---

### BL-02 — Làm sạch mã lỗi chết

**Trạng thái: 🟡 ĐÃ LÀM MỘT PHẦN (2026-09-28)** — xem "Đã xử lý" bên dưới.

**Vấn đề hiện tại:** các mã lỗi khai báo trong `ErrorCode` nhưng **không nơi nào dùng**:

```
VOUCHER_SCOPE_INVALID (4010)              PROMOTION_OVERLAP (4110)
VOUCHER_NOT_IN_WALLET (4014)              INVALID_PROMOTION_SCOPE (4109)
                                          INVALID_PROMOTION_STATUS (4107)
                                          FLASH_PER_USER_LIMIT_REACHED (4310)  ← mã chết có chủ ý
                                          FLASH_STOCK_EXHAUSTED (4309)
```

**Vì sao xảy ra:** luồng voucher dùng **thông báo tiếng Việt inline** qua
`VoucherValidationResponse.invalid("...")` thay vì ném `ErrorCode` — vì endpoint validate
phải trả HTTP 200 kèm cờ `valid`. Các mã kia được khai báo trước rồi không dùng tới.

**Hệ quả:** dev mới đọc `ErrorCode` tưởng có nhiều nhánh lỗi cần xử lý → mất thời gian.

> **⚠️ Đính chính (2026-09-28):** bản đầu của mục này ghi *"13 mã lỗi chết"* và liệt kê
> `INVALID_VOUCHER_DATA` (4009), `VOUCHER_EXPIRED` (4003), `VOUCHER_OUT_OF_STOCK` (4004),
> `VOUCHER_MIN_ORDER_NOT_MET` (4011), `VOUCHER_PER_USER_LIMIT_REACHED` (4012),
> `VOUCHER_NOT_STARTED` (4013) là chết — **SAI**. Kiểm lại thì:
> - `INVALID_VOUCHER_DATA` **có** dùng làm message key trong `@NotEmpty` của
>   `VoucherBulkDeleteRequest` / `VoucherBulkStatusRequest` (grep `ErrorCode.X` không bắt
>   được vì nó là **chuỗi** trong annotation).
> - 5 mã `VOUCHER_*` còn lại nay **đã được dùng thật** ở BL-02 (xem dưới).

**Đã xử lý 2026-09-28 (cùng đợt với việc thêm ErrorCode chi tiết):**

| Mã | Trước | Sau |
|---|---|---|
| `VOUCHER_EXPIRED` (4003) | mã chết | ✅ dùng ở nhánh "hết hạn" |
| `VOUCHER_OUT_OF_STOCK` (4004) | mã chết | ✅ dùng ở nhánh "hết lượt" |
| `VOUCHER_MIN_ORDER_NOT_MET` (4011) | mã chết | ✅ dùng ở nhánh "chưa đủ tối thiểu" |
| `VOUCHER_PER_USER_LIMIT_REACHED` (4012) | mã chết | ✅ dùng ở nhánh "chạm perUserLimit" |
| `VOUCHER_NOT_STARTED` (4013) | mã chết | ✅ dùng ở nhánh "chưa tới ngày" |

**Còn lại cần dọn:** `VOUCHER_SCOPE_INVALID` (4010), `VOUCHER_NOT_IN_WALLET` (4014),
`PROMOTION_OVERLAP` (4110), `INVALID_PROMOTION_SCOPE` (4109), `INVALID_PROMOTION_STATUS`
(4107), `FLASH_STOCK_EXHAUSTED` (4309).

**Phạm vi thay đổi còn lại:** BE — xoá hằng số không dùng **hoặc** đánh dấu `@Deprecated`
kèm comment.

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL02a | Mã lỗi **không có** chỗ dùng → xoá hoặc đánh dấu deprecated, không để mập mờ |
| BR-BL02b | Riêng `FLASH_PER_USER_LIMIT_REACHED` giữ lại kèm comment: **mã chết có chủ ý** (đã chốt: hết suất → về giá thường, không chặn) |
| BR-BL02c | Luồng validate voucher tiếp tục trả **HTTP 200 + message tiếng Việt** (không đổi) |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL02a | Không còn hằng số `ErrorCode` nào không được tham chiếu (trừ mã chết có comment) |
| AC-BL02b | Tài liệu 01/03 khớp với danh sách mã lỗi thật |

**Phụ thuộc:** không.

---

### BL-03 — Flash sale lặp lịch hằng ngày

**Vấn đề hiện tại:** mỗi phiên là **một khoảng thời gian tuyệt đối** (`startAt` →
`endAt`). Muốn chạy khung 9h/12h/19h hằng ngày, admin phải **tạo tay từng phiên**.

**Giá trị nghiệp vụ:** mô hình flash sale phổ biến của các sàn (Shopee/Tiki) là **khung
giờ cố định lặp lại mỗi ngày**. Không có nó thì admin phải tạo phiên thủ công mỗi ngày —
không bền vững.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | Thêm trường `recurrence` (enum `NONE` / `DAILY`) + `startTime`/`endTime` (`LocalTime`) cho phiên lặp. Sửa `FlashSale.isRunning()` và `resolvePriceMap` để tính khung giờ trong ngày |
| DB | Migration mới: thêm cột vào `flash_sales` |
| FE | Form phiên: chọn "Lặp lại hằng ngày" + khung giờ; danh sách hiển thị badge "Hằng ngày"; trang chi tiết bỏ dòng "Không — chưa hỗ trợ" |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL03a | Phiên `DAILY` dùng `startTime`/`endTime` (`LocalTime`), chạy mỗi ngày trong khoảng `startDate`..`endDate` |
| BR-BL03b | Phiên `NONE` giữ nguyên hành vi hiện tại (`startAt`/`endAt` tuyệt đối) — **không hồi quy** |
| BR-BL03c | `soldInFlash` **reset mỗi ngày** với phiên `DAILY` — cần bảng đếm theo ngày, không dùng chung `flash_sale_items.sold_in_flash` |
| BR-BL03d | `perUserLimit` với phiên `DAILY` đếm **trong ngày**, không trong cả khoảng |
| BR-BL03e | FE hiển thị "Kết thúc sau HH:MM:SS" và "Phiên tiếp theo: 19:00 hôm nay" |

> **BR-BL03c là phần khó nhất.** Phiên lặp cần kho **theo ngày**; nếu dùng chung
> `sold_in_flash` thì hôm nay bán hết, mai phiên không mở được. Cần bảng
> `flash_sale_daily_counters(flash_sale_item_id, sale_date, sold)` hoặc tương đương.

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL03a | Tạo phiên `DAILY` 9:00–11:00 → 10:00 hôm nay giá flash hiện; 12:00 cùng ngày giá về thường |
| AC-BL03b | Ngày hôm sau 10:00 → giá flash hiện lại, `soldInFlash` **về 0** |
| AC-BL03c | Phiên `NONE` cũ vẫn chạy y như trước (không hồi quy) |
| AC-BL03d | `perUserLimit` đếm riêng theo từng ngày |

**Phụ thuộc:** BL-01 nên làm trước (cùng nhóm vận hành), nhưng không bắt buộc.

---

### BL-04 — "Nhắc tôi" khi phiên sắp mở

**Vấn đề hiện tại:** tab "Phiên sắp diễn ra" ở `/flash-sale` chỉ **hiển thị** thông tin;
chưa có cách để khách được nhắc khi phiên mở.

**Giá trị nghiệp vụ:** tăng tỉ lệ quay lại của khách vào đúng khung giờ vàng — đây là mục
đích chính của flash sale.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | Bảng `flash_sale_reminders(user_id, flash_sale_id)` + API đăng ký/huỷ. Job quét phiên sắp mở → sinh thông báo |
| DB | Migration mới |
| FE | Nút "Nhắc tôi" / "Đã nhắc" trên card phiên sắp diễn ra; dùng hệ thống thông báo sẵn có (`app-notification-bell`) |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL04a | Chỉ đăng ký được cho phiên **sắp diễn ra** (`startAt > now`), chưa kết thúc |
| BR-BL04b | Mỗi khách mỗi phiên **một** lần đăng ký (unique index) |
| BR-BL04c | Nhắc **một lần** trước `startAt` (VD 15 phút) — không spam |
| BR-BL04d | Khách huỷ được đăng ký trước khi phiên mở |
| BR-BL04e | Khách chưa đăng nhập → nút yêu cầu đăng nhập |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL04a | Bấm "Nhắc tôi" → nút đổi thành "Đã nhắc"; bấm lần 2 không tạo bản ghi trùng |
| AC-BL04b | Trước `startAt` 15 phút → khách nhận đúng **một** thông báo |
| AC-BL04c | Huỷ đăng ký → không nhận thông báo |
| AC-BL04d | Khách chưa đăng nhập bấm "Nhắc tôi" → chuyển tới trang đăng nhập |

**Phụ thuộc:** hệ thống thông báo hiện có (`app-notification-bell` / `user-notification.service`).

---

## NHÓM B — PROMOTION NÂNG CAO

### BL-05 — Chặn `PromotionType` chưa hỗ trợ ⚠️ **BUG CẦN SỬA**

**Trạng thái: ✅ ĐÃ SỬA (2026-09-28)** — thêm `PromotionService.validateType()` chặn
`type != PRODUCT_DISCOUNT` bằng mã mới `UNSUPPORTED_PROMOTION_TYPE` (4113); `type = null`
→ `INVALID_PROMOTION_CONFIG` (4103). 3 test mới trong `PromotionServiceTest`. Quy tắc đã
ghi vào tài liệu chức năng: [02-promotion.md](02-promotion.md) BR-P13 + AC-P13b/AC-P13c.

**Vấn đề gốc:** `PromotionService.validate()` **kiểm tra `discountType` nhưng KHÔNG
kiểm tra `type`**. Enum `PromotionType` có 3 giá trị (`PRODUCT_DISCOUNT`, `GIFT_VOUCHER`,
`BUNDLE`) nhưng **engine hoàn toàn bỏ qua `type`** — nó chỉ đọc `discountType`.

**Hệ quả nghiệp vụ:** admin tạo chương trình `type = GIFT_VOUCHER` (ý định: tặng voucher
khi đơn hoàn tất) → hệ thống **âm thầm** coi nó là giảm giá theo dòng và trừ tiền ngay
trong giỏ. Chương trình chạy **sai hoàn toàn ý định** mà không có cảnh báo nào.

Đây là **lỗi im lặng** — cùng loại với lỗi `maxDiscountAmount` là field chết đã sửa trước
đây: field có trong enum/form nhưng không có tác dụng thật.

**Ghi chú:** FE form **hardcode** `type = 'PRODUCT_DISCOUNT'` (không có ô chọn), nên
không thể chọn sai từ UI — chỉ sai được qua gọi API trực tiếp. Nhưng vẫn phải chặn ở BE
vì admin/dev có thể gọi API, và để nguyên là bẫy cho lần mở rộng sau.

---

### BL-06 — `GIFT_VOUCHER` — tặng voucher khi đơn hoàn tất

> **⚠️ Điều kiện tiên quyết (2026-09-28):** giá trị enum `GIFT_VOUCHER` **đã bị XOÁ** khỏi
> `PromotionType` (xem mục "Đã xoá enum" ở nhóm E). Muốn làm hạng mục này phải **thêm lại
> giá trị vào enum + engine tương ứng + migration mở rộng enum DB** — không chỉ viết service.

**Giá trị nghiệp vụ:** kích cầu mua lại. Khách hoàn tất đơn → nhận voucher cho lần sau.
Đây là cơ chế đã có sẵn một nửa trong mô hình dữ liệu (`UserVoucherSource.GIFTED` vẫn còn)
nhưng **chưa có luồng nào sinh ra**.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | `PromotionType.GIFT_VOUCHER` được mở. Thêm liên kết "chương trình → voucher mẫu được tặng" (`promotion.giftVoucherId`). Hook ở `OrderService.applyStatusChange(COMPLETED)` |
| DB | Migration: thêm cột `gift_voucher_id` vào `promotions` |
| FE | Form chương trình: khi chọn `GIFT_VOUCHER` → hiện picker chọn voucher mẫu |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL06a | Khi đơn chuyển `COMPLETED` → sinh `UserVoucher` (`source = GIFTED`) cho khách, gắn voucher mẫu đã cấu hình |
| BR-BL06b | **Idempotent**: `applyStatusChange` có thể chạy lại → phải kiểm tra đã sinh chưa (unique index `user_id, voucher_id` đã có sẵn để chặn) |
| BR-BL06c | Chương trình `GIFT_VOUCHER` **không** giảm tiền trên đơn — chỉ tặng voucher |
| BR-BL06d | Voucher mẫu hết hạn/hết lượt phát → bỏ qua, không chặn việc hoàn tất đơn |
| BR-BL06e | Đơn bị huỷ sau đó → **không** thu hồi voucher đã tặng (đã trao tay khách) |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL06a | Đơn hoàn tất → voucher xuất hiện trong ví khách với nguồn `GIFTED` |
| AC-BL06b | Đơn đó **không** bị trừ thêm tiền từ chương trình `GIFT_VOUCHER` |
| AC-BL06c | Gọi `applyStatusChange(COMPLETED)` lần 2 → **không** sinh voucher thứ hai |
| AC-BL06d | Voucher mẫu đã hết lượt phát → đơn vẫn hoàn tất bình thường |

**Phụ thuộc:** BL-05 (chặn trước, mở sau).

---

### BL-07 — `BUNDLE` — mua kèm giá ưu đãi

> **⚠️ Điều kiện tiên quyết (2026-09-28):** giá trị enum `BUNDLE` **đã bị XOÁ** khỏi
> `PromotionType`. Phải thêm lại vào enum + engine tương ứng + migration mở rộng enum DB.

**Giá trị nghiệp vụ:** bán chéo — mua laptop kèm chuột/túi thì phụ kiện được giá ưu đãi.
Đây là cơ chế **khác bản chất** với giảm giá theo dòng.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | Bảng `promotion_bundle_items(promotion_id, product_id, bundle_price, required_product_id?)`. Engine mở rộng để xử lý nhóm |
| DB | Migration mới |
| FE | Form chương trình: bảng chọn sản phẩm mua kèm + giá ưu đãi. Trang giỏ: khối "Giá sốc — Mua kèm" ở `cart-line-item` |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL07a | Chỉ áp khi giỏ có **sản phẩm chính** + **sản phẩm kèm** |
| BR-BL07b | Giá kèm **chỉ áp cho số lượng ≤ số lượng sản phẩm chính** |
| BR-BL07c | Không cộng dồn với promotion khác trên cùng dòng kèm |
| BR-BL07d | Hiển thị ở giỏ dưới dạng khối gợi ý "Mua kèm để được giá X" |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL07a | Giỏ có laptop + chuột thuộc bundle → chuột được giá ưu đãi |
| AC-BL07b | Giỏ chỉ có chuột, không có laptop → **không** áp giá bundle |
| AC-BL07c | Mua 1 laptop + 3 chuột → chỉ **1** chuột được giá bundle |

**Phụ thuộc:** BL-05.

---

### BL-08 — `FIXED_PRICE` — ép giá bán cố định

> **⚠️ Điều kiện tiên quyết (2026-09-28):** giá trị enum `FIXED_PRICE` **đã bị XOÁ** khỏi
> `PromotionDiscountType`. Phải thêm lại vào enum + khôi phục nhánh `case FIXED_PRICE`
> trong `PromotionEngine.rawDiscount` + migration mở rộng enum DB.

**Giá trị nghiệp vụ:** "Đồng giá 11.990.000đ" — dạng khuyến mại rất phổ biến.

**Phạm vi thay đổi:** chủ yếu là **mở khoá** + xử lý quy tắc biên.

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL08a | `FIXED_PRICE` = giá bán mới **mỗi máy**; giảm = `(giá hiện tại − giá đích) × số lượng` |
| BR-BL08b | Giá đích **phải < giá bán hiện tại** → validate khi tạo (giống flash `FLASH_PRICE_NOT_LOWER`) |
| BR-BL08c | Giá đích ≥ giá hiện tại → **chặn**, không trả 0đ im lặng |
| BR-BL08d | Với dòng đang có giá flash → flash thắng (không áp `FIXED_PRICE`) |
| BR-BL08e | Nhãn UI: "Giá bán sau giảm (₫/máy)" thay vì "Giá trị giảm" |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL08a | Chương trình `FIXED_PRICE` giá đích 11.990.000, sản phẩm 12.990.000 → giảm đúng 1.000.000đ/máy |
| AC-BL08b | Giá đích ≥ giá sản phẩm → lỗi khi tạo |
| AC-BL08c | Mua 2 máy → giảm 2.000.000đ (per-máy) |

**Phụ thuộc:** BL-05.

---

### BL-09 — `QUANTITY_TIER` — giảm theo bậc số lượng

> **⚠️ Điều kiện tiên quyết (2026-09-28):** giá trị enum `QUANTITY_TIER` **đã bị XOÁ** khỏi
> `PromotionDiscountType`. Phải thêm lại vào enum + migration mở rộng enum DB.

**Giá trị nghiệp vụ:** "Mua 2 giảm 5%, mua 3+ giảm 8%" — khuyến khích mua nhiều.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | Bảng `promotion_tiers(promotion_id, min_quantity, discount_type, discount_value)`; engine mở nhánh `QUANTITY_TIER` |
| DB | Migration mới |
| FE | Form: bảng nhập bậc (số lượng tối thiểu + mức giảm) |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL09a | Chọn **bậc cao nhất** mà `line.quantity ≥ min_quantity` |
| BR-BL09b | Các bậc phải có `min_quantity` tăng dần, không trùng |
| BR-BL09c | Mức giảm của bậc dùng lại `PERCENT` hoặc `AMOUNT` (per-máy) |
| BR-BL09d | `maxDiscountAmount` vẫn cap tổng (D23) |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL09a | Bậc: 2 máy → 5%, 3 máy → 8%. Mua 3 máy → áp **8%**, không phải 5% |
| AC-BL09b | Mua 1 máy → **không** áp bậc nào |
| AC-BL09c | Bậc trùng `min_quantity` → lỗi khi tạo |

**Phụ thuộc:** BL-05.

---

### BL-10 — Cộng dồn nhiều promotion trên một dòng

**Vấn đề hiện tại:** v1 **mỗi dòng chỉ 1 chương trình thắng** (BR-P09). Field `stackable`
có sẵn trong entity và form nhưng **engine bỏ qua hoàn toàn** — đây là **field chết**
cùng loại với `PromotionType`.

**Giá trị nghiệp vụ:** cho phép "giảm 10% + trợ giá 200k" cùng lúc — nhưng **rủi ro lỗ
tiền cao** nếu không kiểm soát.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | `PromotionEngine`: nhóm chương trình `stackable = true` và cộng dồn; giữ trần tổng |
| DB | Không (cột đã có) |
| FE | Form: toggle `stackable` cần ghi chú rõ; preview hiển thị nhiều dòng giảm |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL10a | Chỉ cộng dồn khi **tất cả** chương trình liên quan có `stackable = true` |
| BR-BL10b | Nếu có bất kỳ chương trình `stackable = false` → quay về luật cũ (chọn 1 thắng) |
| BR-BL10c | Trần tổng: `Σ lineDiscount ≤ lineTotal` (không âm dòng) |
| BR-BL10d | `maxDiscountAmount` cap **riêng từng** chương trình |
| BR-BL10e | Thứ tự áp khi cộng dồn: `PERCENT` trước, `AMOUNT` sau (hoặc theo `priority`) — **phải chốt trước khi code** |

> **⚠️ Cảnh báo:** đây là hạng mục **rủi ro cao nhất** trong nhóm B. Cộng dồn nhiều %
> có thể đưa `Cần thanh toán` về 0 hoặc lỗ. Cần chốt BR-BL10e với stakeholder **trước**
> khi viết code, và bổ sung test biên.

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL10a | 2 chương trình `stackable = true` cùng khớp → cả hai đều giảm, tổng = tổng 2 mức |
| AC-BL10b | 1 chương trình `stackable = false` → chỉ 1 thắng như cũ |
| AC-BL10c | Tổng giảm không vượt `lineTotal`; `Cần thanh toán` không âm |
| AC-BL10d | Preview = số trong đơn |

**Phụ thuộc:** BL-05 (để phân biệt rõ field có tác dụng / không tác dụng).

---

## NHÓM C — VOUCHER NÂNG CAO

### BL-11 — Nhiều voucher trên một đơn

**Vấn đề hiện tại:** v1 **tối đa 1 voucher/đơn** (BR-V11) — gửi cả `voucherCode` lẫn
`userVoucherId` → lỗi `VOUCHER_AND_VOUCHER_CONFLICT` (4208).

**Giá trị nghiệp vụ:** các sàn cho phép xếp chồng voucher (voucher sàn + voucher shop).

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | `Order` cần lưu **nhiều** voucher → bảng `order_vouchers`. Tính `eligibleAmount` tuần tự: voucher 1 trên `subtotal − promotion`, voucher 2 trên phần còn lại |
| DB | Migration mới + backfill đơn cũ từ `orders.voucher_id` |
| FE | Overlay cho chọn nhiều voucher từ ví; sidebar hiển thị từng dòng voucher |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL11a | Tối đa **N** voucher/đơn (chốt với stakeholder, đề xuất 2) |
| BR-BL11b | Thứ tự áp: voucher **số tiền trước**, voucher **% sau** (có lợi cho khách) — hoặc theo `priority` |
| BR-BL11c | Mỗi voucher tính trên phần tiền **còn lại** sau voucher trước |
| BR-BL11d | Không cho 2 voucher cùng mẫu |
| BR-BL11e | `perUserLimit` đếm theo **số đơn** có dùng voucher đó, không theo số voucher trong đơn |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL11a | Chọn 2 voucher → cả hai được áp, tổng giảm đúng thứ tự |
| AC-BL11b | Chọn 3 voucher khi trần là 2 → chặn |
| AC-BL11c | Chọn 2 voucher cùng mẫu → chặn |
| AC-BL11d | Đơn cũ (1 voucher) vẫn xem được đúng sau migration |

**Phụ thuộc:** không (nhưng **nên** làm sau BL-10 để thống nhất mô hình "áp tuần tự").

---

### BL-12 — Voucher `WELCOME` / `BIRTHDAY`

**Vấn đề hiện tại:** `UserVoucherSource` có `WELCOME` và `BIRTHDAY` nhưng **không có luồng
nào sinh ra chúng** — chỉ `CLAIMED` (khách tự nhận) và `GIFTED` (admin phát) đang hoạt động.

**Giá trị nghiệp vụ:** voucher chào mừng (kích hoạt người mới) và voucher sinh nhật (giữ
chân khách) — hai đòn tăng trưởng tiêu chuẩn.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | `WELCOME`: hook ở luồng đăng ký tài khoản. `BIRTHDAY`: cần `User.birthday` + job quét hằng ngày |
| DB | Kiểm tra `users` có cột ngày sinh chưa; nếu chưa → migration thêm |
| FE | Trang đăng ký: thêm ô ngày sinh (nếu làm BIRTHDAY); ví hiển thị nhãn nguồn |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL12a | `WELCOME`: tặng **một lần** khi tài khoản được kích hoạt/xác thực |
| BR-BL12b | `BIRTHDAY`: tặng **một lần mỗi năm**, trước ngày sinh N ngày (chốt N) |
| BR-BL12c | Cả hai **idempotent** — unique index `(user_id, voucher_id)` chặn trùng, nhưng voucher sinh nhật năm sau là **voucher mẫu khác** nên không bị chặn |
| BR-BL12d | Voucher mẫu hết lượt phát → bỏ qua, không chặn đăng ký |
| BR-BL12e | Khách chưa có ngày sinh → bỏ qua, không lỗi |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL12a | Đăng ký tài khoản mới → ví có voucher `WELCOME` |
| AC-BL12b | Đăng ký lại/gọi lặp → **không** tặng thêm |
| AC-BL12c | Tới ngày sinh → ví có voucher `BIRTHDAY` |
| AC-BL12d | Năm sau → nhận voucher sinh nhật mới |
| AC-BL12e | Khách không có ngày sinh → không lỗi, không có voucher |

**Phụ thuộc:** BL-01 (job) cho phần BIRTHDAY.

---

## NHÓM D — TRẢI NGHIỆM

### BL-13 — Nhắc voucher sắp hết hạn

**Vấn đề hiện tại:** voucher hết hạn **âm thầm** — khách chỉ biết khi mở ví và thấy ở tab
"Hết hạn". Không có cảnh báo trước.

**Giá trị nghiệp vụ:** tăng tỉ lệ dùng voucher — voucher không dùng được là chi phí marketing
bỏ đi.

**Phạm vi thay đổi:** BE job (mở rộng BL-01) + FE hiển thị nhãn/thông báo.

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL13a | Nhắc trước khi hết hạn N ngày (chốt N, đề xuất 3) |
| BR-BL13b | Mỗi voucher nhắc **một lần** — không spam |
| BR-BL13c | Không nhắc voucher đã `USED` |
| BR-BL13d | FE hiển thị badge "Sắp hết hạn" trên thẻ voucher |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL13a | Voucher còn 2 ngày → khách nhận đúng **một** thông báo |
| AC-BL13b | Voucher đã dùng → không nhắc |
| AC-BL13c | Thẻ voucher trong ví hiển thị badge "Sắp hết hạn" |

**Phụ thuộc:** BL-01.

---

### BL-14 — Báo cáo hiệu quả khuyến mại

**Vấn đề hiện tại:** mỗi màn chi tiết có KPI (lượt dùng, tiền đã giảm, tỉ lệ dùng) nhưng
**không có báo cáo tổng hợp** — không so sánh được hiệu quả giữa các chương trình, không
biết tổng chi phí khuyến mại theo kỳ.

**Giá trị nghiệp vụ:** admin/quản lý cần biết **tiền khuyến mại đã tiêu** và **mang lại
bao nhiêu đơn** để quyết định chương trình tiếp theo.

**Phạm vi thay đổi:**

| Tầng | Thay đổi |
|---|---|
| BE | Endpoint thống kê tổng hợp: theo kỳ, theo loại (promotion/voucher/flash), top chương trình hiệu quả |
| DB | Có thể cần index thêm trên `orders.order_date` + cột giảm giá |
| FE | Trang báo cáo trong `/admin` (hoặc mở rộng Dashboard) |

**Quy tắc dự kiến:**

| Mã | Quy tắc |
|---|---|
| BR-BL14a | Chỉ tính đơn **không huỷ** (`CANCELLED` loại trừ) |
| BR-BL14b | Tổng chi phí khuyến mại = `Σ promotionDiscount + Σ voucherDiscount` |
| BR-BL14c | Doanh thu quy về `Order.totalPrice` (đã trừ giảm) — phân biệt rõ với `subtotal` |
| BR-BL14d | Xếp hạng chương trình theo **số đơn đã áp** và **tổng tiền đã giảm** |
| BR-BL14e | Quyền: `READ_DASHBOARD` (STAFF xem được) |

**Tiêu chí nghiệm thu dự kiến:**

| Mã | Tiêu chí |
|---|---|
| AC-BL14a | Báo cáo tháng hiển thị đúng tổng tiền giảm, khớp với tổng từ các đơn |
| AC-BL14b | Đơn huỷ **không** được tính vào chi phí khuyến mại |
| AC-BL14c | Top chương trình sắp đúng thứ tự theo tiền giảm |
| AC-BL14d | STAFF xem được báo cáo |

**Phụ thuộc:** không.

---

## NHÓM E — BUG RÀ SOÁT 2026-09-28

> Rà soát toàn module (BE + FE, đối chiếu với tài liệu BA) tìm được 6 lỗi **ngoài** 4 lỗi
> đã biết. Tất cả **CHƯA SỬA** — ghi lại để không mất. BL-15 và BL-16 là mức HIGH vì gây
> **sai tiền thật**.

### BL-15 — FE tự tính tiền voucher trên `subtotal` thay vì `eligibleAmount` ⚠️ **HIGH**

**Trạng thái: ✅ ĐÃ SỬA (2026-09-29)** — thêm `userVoucherId` vào `ValidateVoucherRequest`
+ nhánh xử lý trong `OrderService.validateVoucher`; FE (`cart` + `checkout`) bỏ hẳn hàm tự
tính, gọi API cho **cả hai** nhánh. 3 test mới. Quy tắc: **BR-V13** ở
[01-voucher.md](01-voucher.md) + E-V13/E-V14.

**Vị trí gốc:** `cart.component.ts:186-206` · `checkout.component.ts:299-320`

**Vấn đề gốc:** FE **tự viết lại công thức voucher**, dùng `cart.subtotal` làm `eligible` và
xét `minOrderValue` trên `subtotal` — **bỏ qua hoàn toàn phạm vi (scope)** của voucher,
trong khi BE tính trên `eligibleAmount` (BR-V05).

**Kịch bản sai tiền:** giỏ = 5.000.000đ laptop ASUS + 40.000.000đ phụ kiện (subtotal 45tr).
Voucher `scopeType = BRAND`, scope `ASUS`, `discountPercent = 50`:

| | eligible | giảm hiện cho khách |
|---|---|---|
| **BE** | 5tr (chỉ dòng ASUS) | **2.500.000đ** |
| **FE** | 45tr | **22.500.000đ** |

Khách thấy "Cần thanh toán 22.500.000đ", bấm đặt hàng → BE thu 42.500.000đ.
**Lệch 20.000.000đ.**

Nhánh `minOrderValue` lệch ngược: voucher yêu cầu đơn từ 20tr, giỏ chỉ 5tr thuộc scope →
BE **chặn**, FE **cho qua** → khách bấm đặt rồi bị từ chối.

**Gốc sâu hơn:** comment ở `cart.component.ts` ghi *"Chọn voucher thì hỏi BE lại số tiền"*
nhưng `revalidateWithVoucher()` **không hề gọi API** — chỉ set cờ `voucherValid = true`.
Đường gọi BE duy nhất là `validateVoucher()` cho **mã gõ tay**, nên nhánh **chọn từ ví
không bao giờ được BE xác nhận**.

**Cách sửa:** mở rộng `ValidateVoucherRequest` nhận `userVoucherId` (BE tra ví của chính
khách, kiểm trạng thái/hạn, rồi dùng CHUNG `checkVoucherRules` + `calculateEligibleAmount`).
FE gọi API cho cả hai nhánh; `voucherPreviewDiscount()` nay chỉ đọc số BE trả.

**Tiêu chí nghiệm thu:**
- AC-BL15a: Giỏ 5tr ASUS + 40tr phụ kiện, voucher BRAND=ASUS 50% → FE hiện giảm **2.500.000đ**, khớp đơn
- AC-BL15b: Chọn voucher từ ví → có gọi API BE xác nhận
- AC-BL15c: Voucher `minOrderValue` không đạt → FE hiện chặn, không cho bấm đặt

---

### BL-16 — Fallback hết kho flash để lại `promotionDiscount` mồ côi ⚠️ **HIGH**

**Trạng thái: ✅ ĐÃ SỬA (2026-09-29)** — dời bước `consumeStock` lên **TRƯỚC** bước tính
tiền, để giá flash đã chốt xong mới chạy engine + `eligibleAmount`. Quy tắc: **BR-F13** ở
[03-flash-sale.md](03-flash-sale.md) + E-F14.

**Vị trí gốc:** `OrderService.java:286-291` (fallback) · hệ quả ở `:220`, `:231`

**Vấn đề gốc:** khi `consumeStock` trả `false`, code reset dòng đó về giá thường
(`setPrice(product.getPrice())`, `setDiscountAmount(0)`, `setPromotionId(null)`) —
**nhưng** `promotionDiscount`, `discountAmount`, `totalPrice` đã tính xong từ kết quả engine
và **vẫn gồm phần giảm của chính dòng đó**.

**Kịch bản sai tiền:** giỏ chỉ có HP 31.990.000 × 1, promotion 8% → `promotionDiscount` =
2.559.200. Phiên flash còn đúng 1 suất; khách khác chốt trước → `consumeStock` = 0 → dòng
về giá thường 31.990.000, `discountAmount` dòng = 0. Nhưng `Order.promotionDiscount` **vẫn
2.559.200** và `totalPrice` **vẫn 29.430.800** → khách **trả thiếu 2.559.200đ** so với hàng
nhận. Admin đối soát thấy đơn ghi giảm 2.559.200đ mà không dòng nào có `promotionId` →
**sổ không khớp**.

Thêm một tầng sai: `voucherLines` lấy `lr.lineTotal()` theo **giá flash** → `eligibleAmount`
và `voucherDiscount` cũng tính trên giá flash đã mất.

**Cách sửa:** đảo thứ tự — trừ kho phiên TRƯỚC, ghi lại dòng nào thực sự được giá flash
(`flashItemIdByProduct`), rồi mới chạy engine trên `flashMap` đã lọc. Dòng mất flash bị
**loại khỏi `flashMap`** nên engine tự tính lại promotion trên giá thường, và
`eligibleAmount` cũng theo giá mới.

**Tiêu chí nghiệm thu:**
- AC-BL16a: Mô phỏng `consumeStock` fail → `Order.promotionDiscount` = 0, `totalPrice` = giá thường
- AC-BL16b: `Σ OrderDetail.discountAmount` = `Order.promotionDiscount` (sổ khớp)

---

### BL-17 — `Product.quantity` không có optimistic lock 🟡

**Trạng thái: ✅ ĐÃ SỬA (2026-09-28)** — thêm `ProductRepository.deductStock()` /
`restoreStock()` là UPDATE atomic có điều kiện `quantity >= :qty`, set luôn
`updatedAt = CURRENT_TIMESTAMP`. `OrderService.createOrder` ném
`CART_QUANTITY_EXCEEDS_STOCK` khi 0 dòng. 3 test mới. Quy tắc: **BR-A04** ở
[README.md](README.md) §4.1.

**Vấn đề gốc:** hai đơn đồng thời cho cùng máy cuối: cả hai qua check ở `:133-141`, cùng
đọc `quantity = 1`, cùng ghi `0` → **bán 2 máy của 1**. Trái với pattern atomic UPDATE đã
dùng cho `usedCount` (BR-P03) và `consumeStock` (BR-F08).

**Vì sao chọn UPDATE atomic thay vì `@Version`?** `Product.updatedAt` là
`@LastModifiedDate` và **đang được dùng thật** (cột "Ngày sửa" ở màn quản lý sản phẩm).
Nếu dùng `@Version` thì `updatedAt` vẫn được Hibernate ghi, nhưng phải xử lý
`OptimisticLockException` ở tầng service — nhiều thay đổi hơn. UPDATE atomic khớp pattern
có sẵn của dự án (`incrementUsedCount`, `consumeStock`) và set `updatedAt` tường minh.

---

### BL-18 — `Voucher.usedCount` không atomic 🟡

**Trạng thái: ✅ ĐÃ SỬA (2026-09-28)** — thêm `VoucherRepository.incrementUsedCount()` /
`decrementUsedCount()` UPDATE atomic, dùng `COALESCE(usedCount, 0)` vì cột thêm ở Sprint 1
nên voucher cũ có thể NULL. `OrderService.createOrder` ném `VOUCHER_OUT_OF_STOCK` khi 0
dòng; `restorePromotions` dùng bản decrement. 2 test mới. Quy tắc: **BR-A03** ở
[README.md](README.md) §4.1.

**Vấn đề gốc:** hai đơn đồng thời dùng cùng voucher `usageLimit = 1`: cả hai đọc
`usedCount = 0`, cùng ghi `1` → **voucher dùng 2 lần**. `hasReachedPerUserLimit` không bịt
được vì nó đếm theo **từng khách**, còn đây là hạn mức **toàn hệ thống**.

**Vì sao phải ném lỗi mà không âm thầm bỏ voucher?** Khách đã bấm "Đặt hàng" với kỳ vọng
được giảm. Bỏ voucher im lặng → **thu nhiều tiền hơn khách tưởng**. (Khác flash sale: hết
suất thì về giá thường vì đó là *giá*, không phải *phiếu* — xem §4.1 README.)

---

### BL-19 — Thiếu validate dấu của các trường số 🟡

**Trạng thái: ✅ ĐÃ SỬA (2026-09-28)** — thêm `validateNumericBounds` trong
`PromotionService` + `VoucherService`; `PromotionEngine` thêm chốt an toàn cho
`maxDiscountAmount <= 0` (coi như không trần, không phải trần 0). 3 test mới. Quy tắc:
**BR-A05** ở [README.md](README.md) §4.1–4.2.

**Vấn đề gốc:** `maxDiscountAmount = 0` hoặc âm → engine `if (cap != null && sum > cap)` →
`sum > 0` đúng → `excess = sum − max(0, cap) = sum` → **cắt sạch toàn bộ giảm giá**,
chương trình vẫn hiện "đang bật". `usageLimit` âm → `hasBudget()` false → chương trình
**không bao giờ áp**, không cảnh báo.

> **⚠️ Phát hiện khi sửa — `0` KHÁC nghĩa giữa voucher và promotion.** Bản vá đầu tiên của
> mình chặn `<= 0` cho **cả hai**, và **sẽ chặn oan voucher hợp lệ**: với voucher,
> `usageLimit = 0` nghĩa **"không giới hạn"** (UI ghi rõ "Đặt 0 nếu không giới hạn", form
> mặc định `0`). Với promotion, `usageLimit = 0` lại làm chương trình **chết im lặng**
> (`hasBudget()` = `usedCount < 0` luôn false). Đã sửa: voucher chỉ chặn `< 0`, promotion
> chặn `<= 0`. Chi tiết bảng đối chiếu ở [README.md](README.md) §4.2.
>
> **Bài học:** trước khi thêm validate số, phải kiểm tra **cả BE xử lý giá trị đó thế nào**
> **lẫn FE gửi giá trị mặc định gì** — nếu không sẽ chặn oan dữ liệu hợp lệ đang chạy.

---

### BL-20 — N+1 trong `perUserLimit` của flash sale 🟢

**Trạng thái: ✅ ĐÃ SỬA (2026-09-29)** — thêm `sumQtyBoughtByUserForProducts` (1 query gộp
theo phiên, `GROUP BY product`); `resolvePriceMap` giữ lại item/sale đã `JOIN FETCH` thay vì
`findById` lại. Quy tắc: **BR-F14** ở [03-flash-sale.md](03-flash-sale.md). 1 test mới.

**Vấn đề gốc:** `applyPerUserLimit()` gọi `findById` (item) + `findById` (sale) +
`countQtyBoughtByUserInWindow` cho **từng** item → giỏ có N sản phẩm flash thì **3N query**.
Vi phạm chính quy tắc "1 query IN, không N+1" (BR-F06) mà phần base map đã tuân thủ.

**Điểm đáng chú ý:** `findCurrentItems` **đã** `JOIN FETCH` sẵn `product` + `flashSale`, nên
2 lần `findById` là hoàn toàn thừa — dữ liệu đã nằm trong tay. Chỉ cần truyền nó xuống
thay vì dựng `FlashPriceView` rồi query ngược lại từ id.

**Cách sửa:** gom item theo `flashSaleId`, mỗi phiên gọi 1 query đếm cho cả nhóm sản phẩm.
Chỉ hỏi những phiên **thực sự có** item đặt `perUserLimit` — phiên không giới hạn thì không
cần biết khách đã mua bao nhiêu (test `khongGioiHan_giuNguyenView` bắt được đúng chỗ này).

---

## PHỤ LỤC — Ghi chú kỹ thuật cho sprint sau

### P.1 Nguyên tắc chung rút ra từ module này

| # | Nguyên tắc | Nguồn gốc |
|---|---|---|
| 1 | **Enum value không có nhánh xử lý thì XOÁ khỏi enum** — không "để dành rồi chặn ở service". Giữ lại tạo lựa chọn sai trong hợp đồng API + sinh mã lỗi vô nghĩa | Đã dọn `GIFT_VOUCHER`/`BUNDLE`/`FIXED_PRICE`/`QUANTITY_TIER`/`VoucherType.GIFT` (2026-09-28) |
| 2 | **Field tiền trên form phải kiểm tra BE có thực sự đọc không**, và preview FE có khớp công thức BE không | `maxDiscountAmount` từng làm lệch preview/đơn |
| 3 | **Preview và chốt đơn dùng đúng một hàm** — không viết lại logic tính tiền | BR-P02, BR-V01 |
| 4 | **Mọi trừ kho/hạn mức dùng UPDATE atomic có điều kiện**, không đọc-rồi-ghi | BR-P03, BR-F08 |
| 5 | **Huỷ đơn phải hoàn đủ**: kho + voucher + `usedCount` + kho phiên, cùng transaction | BR-V09 |
| 6 | **Snapshot xuống dòng đơn** — chương trình tắt sau đó không làm sai đơn cũ | BR-P07, `OrderDetail.promotionId` |
| 7 | **Mã lỗi khai báo mà không dùng = nợ kỹ thuật** | BL-02 |
| 8 | **FE không bao giờ tự tính tiền** — mọi con số tiền phải hỏi BE | BL-15 |

### P.2 Trường/giá trị hiện đang là "field chết" (cần xử lý)

| Trường | Trạng thái | Hạng mục |
|---|---|---|
| `Promotion.type` | Enum còn 1 giá trị `PRODUCT_DISCOUNT`; engine không đọc | ✅ đã dọn (BR-P13 cũ) |
| `Promotion.stackable` | Có trong entity, engine **bỏ qua** (FE form không hiện) | BL-10 (ghi rõ trong javadoc) |
| `Voucher.scopeValue` | `@Deprecated`, giữ cho `ddl-auto=validate` | Không cần làm — cố ý |
| `UserVoucherSource.WELCOME`/`BIRTHDAY` | Enum có, **không luồng nào sinh** | BL-12 |
| `PromotionResponse.ScopeItem`/`ExcludeItem` | Khai báo nested class, **không dùng** | BL-02 (dọn) |
| 2 mã `ErrorCode` | Khai báo, **không dùng**: 4010, 4310 | BL-02 (phần còn lại) |
| `FLASH_PER_USER_LIMIT_REACHED` (4310) | Mã chết **có chủ ý** | Giữ + comment |

> **Đã dọn 2026-09-28:** xoá 4 giá trị enum không có engine xử lý —
> `PromotionType.GIFT_VOUCHER`/`BUNDLE`, `PromotionDiscountType.FIXED_PRICE`/`QUANTITY_TIER`
> — và `VoucherType.GIFT`. Kèm theo xoá 5 mã `ErrorCode` chỉ tồn tại để báo "chưa hỗ trợ":
> `UNSUPPORTED_PROMOTION_TYPE` (4113), `UNSUPPORTED_PROMOTION_DISCOUNT_TYPE` (4112),
> `INVALID_PROMOTION_STATUS` (4107), `INVALID_PROMOTION_SCOPE` (4109),
> `PROMOTION_OVERLAP` (4110), `VOUCHER_NOT_IN_WALLET` (4014), `FLASH_STOCK_EXHAUSTED` (4309).
>
> **Nguyên tắc:** enum value không có nhánh xử lý thì **xoá khỏi enum**, không "để dành".
> Giữ lại rồi chặn ở service = vừa tạo lựa chọn sai trong hợp đồng API, vừa sinh mã lỗi vô
> nghĩa. Muốn làm thật thì thêm lại **kèm engine**. Xem phụ lục P.1 nguyên tắc #1.

### P.3 Định nghĩa "xong" cho sprint sau

Kế thừa tiêu chí hoàn thành của module (mục 9 trong plan gốc), bổ sung cho hạng mục mới:

1. BE build xanh (`mvn clean package -Djacoco.skip=true`)
2. FE build xanh (`ng build` → 0 error)
3. **Không hồi quy** hành vi đã chốt ở tài liệu 01–04 (đặc biệt: preview = chốt đơn,
   `Cần thanh toán` không âm, đơn cũ không đổi tiền)
4. **Không tạo thêm field chết mới** — mọi enum/field thêm vào phải có đường xử lý hoặc bị chặn
5. Cập nhật tài liệu BA tương ứng (01–04) và chuyển hạng mục từ 05 sang tài liệu chức năng
