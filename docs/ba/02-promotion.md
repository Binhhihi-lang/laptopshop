# 02 — PROMOTION (Chương trình khuyến mại)

> Tài liệu nghiệp vụ · v1.0 · 2026-09-27
> Đọc kèm: [README.md](README.md) §4 (công thức tính tiền) · [01-voucher.md](01-voucher.md)

---

## 1. Mục tiêu & phạm vi

### 1.1 Mục tiêu

Cho phép admin tạo **chương trình khuyến mại tự động**: khi khách thêm sản phẩm vào
giỏ, hệ thống tự tìm chương trình khớp điều kiện và **giảm giá theo từng dòng sản phẩm**
— khách **không cần chọn**, không cần nhập mã.

Promotion là cơ chế **cấp DÒNG**: giảm trên từng sản phẩm khớp phạm vi, khác Voucher
giảm một lần trên cả đơn.

### 1.2 Trong phạm vi

- Admin tạo/sửa/**ngừng áp** chương trình (không xoá cứng), bật/tắt hàng loạt.
- Cấu hình: hình thức giảm (% / số tiền mỗi máy), phạm vi (nhiều giá trị theo danh
  mục/hãng/sản phẩm), **danh sách loại trừ**, thời gian, đơn tối thiểu, số lượng tối
  thiểu, độ ưu tiên, ngân sách theo đơn, trần giảm.
- Engine tự áp khi khách xem giỏ / checkout / đặt hàng.
- Hiển thị cho khách ở overlay "Khuyến mại và ưu đãi".
- Snapshot tiền giảm xuống từng dòng đơn — chương trình tắt sau đó không làm sai đơn cũ.
- Hoàn ngân sách khi huỷ đơn.

### 1.3 Ngoài phạm vi

- Cộng dồn nhiều chương trình trên cùng một dòng (v1: **mỗi dòng 1 ưu đãi tốt nhất**).

> **Cập nhật 2026-09-28 — 4 giá trị enum đã bị XOÁ.** Trước đây `PromotionType` có
> `GIFT_VOUCHER`/`BUNDLE` và `PromotionDiscountType` có `FIXED_PRICE`/`QUANTITY_TIER`, ghi
> là "để dành Sprint 5". Nhưng **không giá trị nào có nhánh xử lý thật** — engine chỉ đọc
> `discountType` (nên `type = GIFT_VOUCHER` bị hiểu nhầm thành giảm giá theo dòng, trừ tiền
> ngay trong giỏ), còn `FIXED_PRICE`/`QUANTITY_TIER` bị service chặn. Giữ chúng trong enum
> chỉ tạo ra lựa chọn sai. Đã xoá khỏi enum Java + siết enum DB (migration **V12**) + xoá
> khỏi FE model + mockup. Khi nào làm thật thì thêm lại **kèm engine tương ứng**.
> Chi tiết: [05-backlog.md](05-backlog.md) BL-06 → BL-09.

---

## 2. Actor

| Actor | Mô tả | Quyền |
|---|---|---|
| **Admin** | Quản trị viên | Toàn quyền promotion |
| **Staff** | Nhân viên | Tạo/xem/sửa — **không có quyền xoá** (và cũng không có quyền này tồn tại) |
| **Customer** | Khách mua hàng | Được hưởng tự động, xem trong overlay |
| **Hệ thống (engine)** | `PromotionEngine` | Tính toán, không có UI |

---

## 3. Use Case

### UC-P01 — Admin tạo chương trình khuyến mại
**Actor:** Admin/Staff · **Tiền điều kiện:** `CREATE_PROMOTION`

**Luồng chính:**
1. Admin mở form tạo chương trình.
2. Nhập: tên (nội bộ), tiêu đề (hiển thị cho khách), mô tả, **hình thức giảm** + giá trị,
   thời gian bắt đầu/kết thúc, phạm vi, loại trừ, điều kiện (đơn tối thiểu, số lượng tối
   thiểu), độ ưu tiên, ngân sách, trần giảm.
3. Hệ thống kiểm tra: tên không rỗng, khoảng thời gian hợp lệ, hình thức giảm hợp lệ,
   phạm vi có giá trị (nếu không phải `ALL`).
4. Lưu chương trình, `usedCount = 0`, `active` theo form.

**Luồng phụ:**
- *Tên rỗng* → `PROMOTION_NAME_REQUIRED` (4102).
- *`endDate` ≤ `startDate`* → `INVALID_PROMOTION_DATE_RANGE` (4106).
- *Phần trăm ngoài 1–100* → `INVALID_PROMOTION_PERCENT` (4104).
- *Số tiền ≤ 0* → `INVALID_PROMOTION_AMOUNT` (4105).
- *`scopeType != ALL` nhưng danh sách giá trị rỗng* → `PROMOTION_SCOPE_REQUIRED` (4108).

> **BR-P05 — Chặn ở tầng service, không chỉ DTO.** Admin có thể gọi API trực tiếp, và
> ràng buộc "percent XOR amount" không diễn đạt được bằng annotation đơn giản.

> **Lưu ý về "loại chương trình":** `Promotion.type` **không còn** là thứ admin chọn — enum
> chỉ có một giá trị `PRODUCT_DISCOUNT`. FE hardcode giá trị này khi gửi lên. Field vẫn giữ
> trong entity/DB để khi thêm loại mới thì không phải đổi schema.

---

### UC-P02 — Admin cập nhật chương trình
**Actor:** Admin/Staff · **Tiền điều kiện:** `UPDATE_PROMOTION`

**Luồng chính:**
1. Admin sửa thông tin.
2. Phạm vi và danh sách loại trừ được **dựng lại toàn bộ** từ dữ liệu form (dùng
   `HashSet` để tự loại giá trị trùng).
3. Lưu.

---

### UC-P03 — Admin ngừng áp chương trình
**Actor:** Admin/Staff · **Endpoint:** `DELETE /api/v1/admin/promotions/{id}`

**Luồng chính:**
1. Admin bấm "Xoá" (hoặc "Ngừng áp") trên bảng.
2. Hệ thống set `active = false` — **không xoá cứng**.

> **BR-P07 — Vì sao không xoá cứng?** Đơn cũ còn tham chiếu tới promotion đã áp
> (`OrderDetail.promotionId`). Xoá sẽ làm hỏng lịch sử đơn hàng — admin không tra được
> đơn đó đã được giảm bởi chương trình nào.

> **Vì sao endpoint dùng `DELETE` nhưng quyền là `UPDATE_PROMOTION`?** Giữ URL quen thuộc
> với FE. Hệ thống **không có** quyền `DELETE_PROMOTION` vì bản chất không phải xoá.

**Ngoài ra:** `PATCH /bulk-status` (bật/tắt lô) và `PATCH /bulk-deactivate` (ngừng áp lô).

---

### UC-P04 — Hệ thống tự áp khuyến mại khi khách xem giỏ
**Actor:** Hệ thống (engine) · **Trigger:** khách mở giỏ/checkout

**Luồng chính:**
1. Hệ thống lọc thô ở DB: chương trình `active`, trong khung thời gian, chưa xoá mềm.
2. Chạy `PromotionEngine.resolve(cartLines, promotions, now)`.
3. Với mỗi dòng: tìm chương trình khớp (scope + loại trừ + `minQuantity` + `minOrderValue`
   + còn ngân sách) → chọn **một** chương trình thắng → tính `lineDiscount`.
4. Cap `lineDiscount ≤ lineTotal` và cap tổng theo `maxDiscountAmount` từng chương trình.
5. Trả về `promotionDiscount`, `lineDiscounts`, danh sách chương trình đã áp.
6. FE hiển thị ở overlay "Đã chọn N ưu đãi và khuyến mại".

> **BR-P01 — Engine là HÀM THUẦN.** Không chạm DB, mọi điều kiện xét trên danh sách
> truyền vào. Nhờ vậy test được toàn bộ ma trận quyết định mà không cần Spring context.

> **BR-P02 — Preview và chốt đơn dùng ĐÚNG MỘT hàm.** `CartService` và `OrderService`
> gọi cùng `PromotionEngine.resolve()`. Viết 2 chỗ là bug — khách thấy số khác lúc chốt.

---

### UC-P05 — Khách đặt hàng (promotion ghi vào đơn)
**Actor:** Customer · **Endpoint:** `POST /client/orders`

**Luồng chính:**
1. Hệ thống dựng đơn từ **giỏ server**.
2. Kiểm tra tồn kho lần cuối.
3. Chạy engine → `promotionDiscount` + kết quả từng dòng.
4. **Tăng `usedCount` atomic** cho **mỗi** chương trình đã áp — 1 lần/đơn, không theo dòng.
5. Snapshot `OrderDetail.discountAmount` + `promotionId` cho từng dòng được giảm.
6. Ghi `Order.promotionDiscount`; `Order.discountAmount` = tổng (promotion + voucher).

**Luồng phụ:**
- *Chương trình vừa hết lượt vì khách khác chốt song song* → `PROMOTION_OUT_OF_STOCK` (4111),
  transaction rollback (không để lại lượt đã tăng cho chương trình khác của cùng đơn).

> **BR-P03 — `usedCount` tăng bằng UPDATE atomic.** Câu `UPDATE ... WHERE usedCount <
> usageLimit` để DB tự chặn; service đếm số dòng bị ảnh hưởng, 0 dòng = hết lượt. Nếu
> đọc-rồi-ghi thì 2 request song song cùng vượt ngân sách.

> **BR-P04 — `usedCount` đếm theo ĐƠN, không theo dòng.** Một chương trình giảm 3 dòng
> trong cùng một đơn thì `usedCount` chỉ tăng **1**.

---

### UC-P06 — Hoàn ngân sách khi huỷ đơn
**Actor:** Customer / Admin / Job

- Với **từng** `promotionId` distinct trong các dòng đơn → `usedCount` −1 (atomic, chặn dưới 0).
- Cùng transaction với việc đổi trạng thái đơn.
- Chi tiết đầy đủ ở [01-voucher.md](01-voucher.md) UC-V09.

---

## 4. Quy tắc nghiệp vụ

### 4.1 Điều kiện áp một chương trình lên một dòng

Xét theo thứ tự (tất cả phải đúng):

| # | Điều kiện | Ghi chú |
|---|---|---|
| 1 | `active = true` | |
| 2 | `startDate ≤ now ≤ endDate` | |
| 3 | Còn ngân sách: `usageLimit == null` hoặc `usedCount < usageLimit` | Đếm theo ĐƠN |
| 4 | `minQuantity == null` hoặc `line.quantity ≥ minQuantity` | |
| 5 | `minOrderValue == null` hoặc `subtotal ≥ minOrderValue` | Xét trên **cả giỏ** |
| 6 | Khớp phạm vi (scope) | Xem 4.2 |
| 7 | **Không** nằm trong danh sách loại trừ | Loại trừ **thắng** scope |
| 8 | Mức giảm tính ra > 0 | Chương trình giảm 0đ không được coi là thắng |
| 9 | Dòng **không** có `flashPrice` | D25 — flash thắng, xem 4.4 |

### 4.2 Khớp phạm vi (scope)

| `scopeType` | Khớp khi |
|---|---|
| `ALL` **hoặc** danh sách rỗng | Luôn khớp (giảm cả giỏ) |
| `CATEGORY` | `targetValue == Product.category.id` |
| `BRAND` | `targetValue == Product.factory` (so **không phân biệt hoa/thường**) |
| `PRODUCT` | `targetValue == Product.id` |

- Chương trình có **nhiều dòng scope** → khớp **BẤT KỲ** dòng nào.
- **BR-P08 — `BRAND` chuẩn hoá trim + UPPERCASE khi lưu** vì so khớp với `Product.factory`
  (không có bảng Brand riêng). Tránh "ASUS" vs "asus" không khớp nhau.

### 4.3 Chọn chương trình thắng cho một dòng

Khi nhiều chương trình cùng khớp một dòng:

1. **Độ ưu tiên (`priority`) cao hơn thắng.**
2. Hoà priority → **mức giảm lớn hơn thắng**.
3. Vẫn hoà → giữ chương trình gặp trước.

> **BR-P09 — Mỗi dòng chỉ nhận 1 chương trình.** Đơn giản, dễ giải thích cho khách.
> Field `stackable` có sẵn trong entity nhưng **v1 bỏ qua** (để dành mở rộng sau).

### 4.4 Tương tác với Flash Sale

- **BR-P10 — Flash thắng promotion trên cùng một dòng.** Dòng đã có `flashPrice` thì
  engine **bỏ qua** (`lineDiscount = 0`). Giá flash đã là giá sâu nhất; cộng dồn % trên
  giá flash sẽ lỗ.

### 4.5 Cách tính mức giảm

| `discountType` | Cấp | Công thức |
|---|---|---|
| `PERCENT` | dòng | `lineTotal × min(discountValue, 100) / 100` |
| `AMOUNT` | **mỗi máy** | `discountValue × quantity` |

> **BR-P11 — `AMOUNT` là per-máy, cố ý khác Voucher.** Mua 2 máy với mức 500k → giảm
> **1.000.000đ**. Đây là cách các sàn ký hiệu ưu đãi trên **đơn giá** ("Trợ giá 500k").

### 4.6 Các trần (cap)

| Cap | Giới hạn |
|---|---|
| `lineDiscount ≤ lineTotal` | Không bao giờ giảm quá giá trị dòng |
| `Σ lineDiscount` của một chương trình `≤ maxDiscountAmount` | Cap **toàn đơn** cho chương trình đó (D23) |
| `promotionDiscount ≤ subtotal` | Tổng giảm không vượt tổng tiền hàng |

> **BR-P12 — Cap theo TỪNG chương trình, trên tổng các dòng nó thắng** — không cap lẻ
> từng dòng. Chương trình "500k/máy, cap 1tr" mua 5 máy → giảm **1.000.000đ**, không
> phải 2.500.000đ. Khi cap bị vượt, phần dư bị cắt dần theo thứ tự các dòng.

### 4.7 Cờ cộng dồn (v1)

| Mã | Quy tắc |
|---|---|
| **BR-P14** | `stackable` **chưa hoạt động ở v1** — engine luôn chọn 1 chương trình/dòng, không đọc cờ này. Admin bật cờ sẽ nhận mức giảm của MỘT chương trình, **không** phải cộng dồn |

> **BR-P14 — `stackable` là field chưa hoạt động, KHÔNG phải bug.** Cờ này **không gây sai
> tiền** (engine bỏ qua nó và vẫn chọn đúng 1 chương trình tốt nhất), chỉ là admin bật lên
> mà không thấy tác dụng. FE form hiện **không hiển thị** ô này. Khi mở BL-10 sẽ kích hoạt.
> Đã ghi rõ trong javadoc entity `Promotion.stackable`.

> **Nguyên tắc rút ra (2026-09-28):** enum value **không có engine xử lý thì phải XOÁ khỏi
> enum**, không phải "để dành". Cách cũ — giữ giá trị trong enum rồi chặn ở service — vừa
> tạo lựa chọn sai trong hợp đồng API, vừa sinh mã lỗi chỉ để báo "chưa hỗ trợ". Đã xoá
> `GIFT_VOUCHER`/`BUNDLE`/`FIXED_PRICE`/`QUANTITY_TIER`; khi làm thật thì thêm lại kèm
> engine tương ứng.

---

## 5. Luồng nghiệp vụ

```
┌─ ADMIN ──────────────────────────────────────────────────────────────┐
│  Tạo chương trình: hình thức giảm + phạm vi + loại trừ + thời gian    │
│  + điều kiện (đơn tối thiểu, SL tối thiểu) + ưu tiên + ngân sách      │
│                        │                                              │
│                        ▼                                              │
│  (ngừng áp = set active=false, KHÔNG xoá cứng)                        │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
┌─ HỆ THỐNG (engine) ────▼─────────────────────────────────────────────┐
│  Khách xem giỏ / checkout / đặt hàng                                 │
│      │                                                               │
│      ▼                                                               │
│  Lọc thô ở DB: active + trong khung giờ                              │
│      │                                                               │
│      ▼                                                               │
│  PromotionEngine.resolve(lines, promotions, now)  ← HÀM THUẦN         │
│      ├─ mỗi dòng: lọc khớp scope/exclude/minQty/minOrder/budget       │
│      ├─ chọn winner: priority cao → giảm nhiều hơn                    │
│      ├─ lineDiscount = min(raw, lineTotal)                            │
│      └─ cap Σ theo maxDiscountAmount từng chương trình                 │
│      │                                                               │
│      ▼                                                               │
│  PREVIEW (giỏ) ────────────┐      CHỐT ĐƠN ────────────────────┐     │
│  hiện overlay              │      + usedCount +1 (atomic, D19) │     │
│                            │      + snapshot OrderDetail        │     │
│                            │      + Order.promotionDiscount     │     │
└────────────────────────────┴──────────────────────────────────┴─────┘
                             │
                             ▼
                    Huỷ đơn → usedCount −1 (distinct promotionId)
```

---

## 6. Data Dictionary

### 6.1 Bảng `promotions` — chương trình (entity `Promotion`)

| Cột | Kiểu | Null | Ý nghĩa |
|---|---|:--:|---|
| `id` | varchar(255) | ✗ | Khoá chính (UUID) |
| `name` | varchar(255) | ✗ | Tên nội bộ (admin tìm/hiện trong danh sách) |
| `title` | varchar(255) | ✓ | Tiêu đề hiển thị cho khách ở overlay |
| `description` | varchar(255) | ✓ | Mô tả |
| `type` | enum(`PRODUCT_DISCOUNT`) | ✗ | Loại chương trình — v1 chỉ 1 giá trị |
| `discount_type` | enum(`AMOUNT`,`PERCENT`) | ✗ | Hình thức giảm |
| `discount_value` | bigint | ✗ | Giá trị giảm (1–100 nếu PERCENT; đơn vị ₫ **mỗi máy** nếu AMOUNT) |
| `max_discount_amount` | bigint | ✓ | Trần giảm **trong một đơn**; null = không trần |
| `min_order_value` | bigint | ✓ | Đơn tối thiểu (xét cả giỏ); null = không yêu cầu |
| `min_quantity` | int | ✓ | Số lượng tối thiểu **trên một dòng**; null = không yêu cầu |
| `start_date` | datetime(6) | ✗ | Bắt đầu |
| `end_date` | datetime(6) | ✗ | Kết thúc |
| `active` | bit(1) | ✗ | Còn áp dụng |
| `priority` | int | ✓ | Ưu tiên (cao thắng); mặc định 0 |
| `stackable` | bit(1) | ✗ | Cho cộng dồn — **v1 không dùng** |
| `usage_limit` | int | ✓ | Ngân sách theo **ĐƠN**; null = không giới hạn |
| `used_count` | int | ✓ | Số đơn đã áp — **chỉ UPDATE atomic**, không set trực tiếp |
| `created_at` / `updated_at` | datetime(6) | ✓ | Audit |
| `deleted_at` | datetime(6) | ✓ | Xoá mềm |

### 6.2 Bảng `promotion_scopes` — phạm vi (entity `PromotionScope`)

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | varchar(255) | Khoá chính |
| `promotion_id` | varchar(255) | FK → `promotions.id` |
| `target_type` | enum(`ALL`,`BRAND`,`CATEGORY`,`PRODUCT`) | Loại phạm vi |
| `target_value` | varchar(255) | `Category.id` \| `Product.factory` \| `Product.id` |

### 6.3 Bảng `promotion_excludes` — loại trừ (entity `PromotionExclude`)

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | varchar(255) | Khoá chính |
| `promotion_id` | varchar(255) | FK → `promotions.id` |
| `product_id` | varchar(255) | Id sản phẩm bị chừa ra — **cố ý KHÔNG có FK** tới `products` |

> **Vì sao `product_id` không có FK?** Lưu dạng chuỗi để việc xoá sản phẩm không kéo theo
> ràng buộc khoá ngoại và không làm hỏng chương trình đang chạy.

> **Loại trừ thắng phạm vi:** sản phẩm nằm trong danh sách loại trừ **không bao giờ** được
> giảm, kể cả khi khớp scope. VD: giảm 10% danh mục Laptop nhưng chừa MacBook.

### 6.4 Enum

| Enum | Giá trị | Ghi chú |
|---|---|---|
| `PromotionType` | `PRODUCT_DISCOUNT` | Loại duy nhất v1 tính tiền |
| `PromotionDiscountType` | `PERCENT` / `AMOUNT` | Đúng hai nhánh engine thực sự tính |

### 6.5 Cột liên quan trên bảng khác

| Bảng | Cột | Ý nghĩa |
|---|---|---|
| `orders` | `promotion_discount` | Tổng giảm promotion của đơn |
| `order_detail` | `discount_amount` | Tiền giảm **riêng dòng này** (snapshot) |
| `order_detail` | `promotion_id` | Id chương trình đã áp cho dòng (snapshot, null nếu không giảm) |

---

## 7. API Contract

### 7.1 Admin — `/api/v1/admin/promotions`

| Method | Path | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/promotions` | `READ_PROMOTION` | — | `List<PromotionResponse>` |
| GET | `/promotions/{id}` | `READ_PROMOTION` | — | `PromotionResponse` |
| POST | `/promotions` | `CREATE_PROMOTION` | `@RequestBody PromotionCreationRequest` (JSON) | `PromotionResponse` |
| PUT | `/promotions/{id}` | `UPDATE_PROMOTION` | `PromotionUpdateRequest` (JSON) | `PromotionResponse` |
| DELETE | `/promotions/{id}` | **`UPDATE_PROMOTION`** | — | — (ngừng áp) |
| PATCH | `/promotions/bulk-status` | `UPDATE_PROMOTION` | `{ ids, active }` | — |
| PATCH | `/promotions/bulk-deactivate` | `UPDATE_PROMOTION` | `{ ids }` | — |

**Vì sao promotion dùng JSON còn voucher dùng form-data?** Promotion không có ảnh →
không cần multipart.

**`PromotionCreationRequest`:**

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:--:|---|
| `name` | String | ✓ | Tên nội bộ |
| `title` | String | | Hiển thị cho khách |
| `description` | String | | |
| `type` | PromotionType | ✓ | |
| `discountType` | PromotionDiscountType | ✓ | |
| `discountValue` | Long | ✓ | `@Min(1)` |
| `maxDiscountAmount` | Long | | |
| `startDate` | LocalDateTime | ✓ | |
| `endDate` | LocalDateTime | ✓ | |
| `active` | Boolean | | Mặc định true |
| `priority` | Integer | | |
| `minOrderValue` | Long | | |
| `minQuantity` | Integer | | |
| `usageLimit` | Integer | | |
| `stackable` | Boolean | | v1 bỏ qua |
| `scopeType` | ScopeType | ✓ | |
| `scopeValues` | List\<String\> | | |
| `excludeProductIds` | List\<String\> | | |

`PromotionUpdateRequest extends PromotionCreationRequest` — cùng trường.

**`PromotionResponse`:** toàn bộ trường trên + `usedCount`, `createdAt`, `updatedAt`.

---

### 7.2 Client

Promotion **không có endpoint riêng** cho khách — nó được trả kèm trong response giỏ hàng.

**`CartResponse` (rút gọn phần liên quan):**

| Trường | Ý nghĩa |
|---|---|
| `subtotal` | Tổng tiền hàng (đã gồm giá flash) |
| `shippingFee` | Phí ship |
| `total` | `subtotal + shippingFee` (chưa trừ voucher) |
| `promotionDiscount` | Tổng giảm từ promotion |
| `payable` | Số phải trả = `subtotal + shippingFee − promotionDiscount` |
| `promotions[]` | Danh sách chương trình đã áp: `{ id, name, title, discountType, discountValue, maxDiscountAmount, minOrderValue, minQuantity, usageLimit, discountAmount }` |
| `items[].lineDiscount` | Tiền giảm riêng từng dòng |
| `items[].flashPrice` | Giá flash nếu dòng đang trong phiên |

> **Chỉ chương trình thực sự giảm tiền mới xuất hiện** trong `promotions[]` — FE không
> hiện dấu "✓" cho ưu đãi vô hiệu.

**Đặt hàng:** `CreateOrderRequest` **không** có trường nào cho promotion — khách không
chọn được, hệ thống tự áp.

---

### 7.3 Bảng mã lỗi

| Code | Hằng số | HTTP | Thông báo |
|---|---|---|---|
| 4101 | `PROMOTION_NOT_FOUND` | 404 | Không tìm thấy chương trình khuyến mại |
| 4102 | `PROMOTION_NAME_REQUIRED` | 400 | Tên chương trình khuyến mại không được để trống |
| 4103 | `INVALID_PROMOTION_CONFIG` | 400 | Cấu hình khuyến mại không hợp lệ |
| 4104 | `INVALID_PROMOTION_PERCENT` | 400 | Phần trăm giảm giá phải nằm trong khoảng 1–100 |
| 4105 | `INVALID_PROMOTION_AMOUNT` | 400 | Số tiền giảm giá phải lớn hơn 0 |
| 4106 | `INVALID_PROMOTION_DATE_RANGE` | 400 | Thời gian kết thúc phải sau thời gian bắt đầu |
| 4107 | `INVALID_PROMOTION_STATUS` | 400 | Trạng thái chương trình không hợp lệ |
| 4108 | `PROMOTION_SCOPE_REQUIRED` | 400 | Phạm vi áp dụng không được để trống khi không chọn toàn bộ đơn |
| 4109 | `INVALID_PROMOTION_SCOPE` | 400 | Phạm vi áp dụng của chương trình không hợp lệ |
| 4110 | `PROMOTION_OVERLAP` | 400 | Khoảng thời gian bị trùng với chương trình khác cùng phạm vi |
| 4111 | `PROMOTION_OUT_OF_STOCK` | 400 | Chương trình vừa hết lượt, đơn được tính lại giá mới |

> **Đã xoá 2026-09-28** (không còn nhánh nào dùng): `INVALID_PROMOTION_STATUS` (4107),
> `INVALID_PROMOTION_SCOPE` (4109), `PROMOTION_OVERLAP` (4110),
> `UNSUPPORTED_PROMOTION_DISCOUNT_TYPE` (4112), `UNSUPPORTED_PROMOTION_TYPE` (4113).
> Hai mã `UNSUPPORTED_*` từng được thêm cho BR-P13/BR-P06 — sau khi xoá 4 giá trị enum thì
> không còn trạng thái nào để báo lỗi nữa.

---

## 8. Màn hình & tương tác

### 8.1 Admin

| Màn | Nội dung |
|---|---|
| **Danh sách Khuyến mại** (`/admin/promotions`) | Bảng + bulk (bật/tắt, ngừng áp) + menu kebab |
| **Form Khuyến mại** (`/admin/promotions/create`, `/:id/edit`) | Tên/tiêu đề/mô tả, hình thức giảm + giá trị, **trần giảm luôn hiện** mọi kiểu giảm, đơn tối thiểu, SL tối thiểu, thời gian, ưu tiên, ngân sách, `active`, **scope-picker**, **danh sách loại trừ** (product-picker), panel **preview tính tiền** |
| **Chi tiết Khuyến mại** (`/admin/promotions/:id`) | KPI 4 thẻ, thông tin, phạm vi, timeline, bảng đơn đã áp, khối hiệu quả |

> **Quy ước quan trọng (đã sửa):** trần giảm (`maxDiscountAmount`) **luôn hiện** với mọi
> hình thức giảm, nhãn "Giảm tối đa / đơn (₫)". Trước đây form ẩn trần khi chọn `PERCENT`
> — **sai ngược**: `PERCENT` tính trên tổng dòng (không phình theo số lượng), còn
> `AMOUNT` mới **per-máy × số lượng** nên mới cần trần thì lại bị ẩn.

> **Quy ước UI:** bám mockup `design-mockup/promotion-admin-preview.html` — tab + tick +
> chip cho phạm vi, **không** dùng `<select>`. Không lộ thuật ngữ kỹ thuật ra UI.

### 8.2 Client

Promotion hiển thị trong **overlay "Ưu đãi và khuyến mại"** mở từ nút ở cả trang giỏ và
trang thanh toán: mục "Khuyến mại" liệt kê chương trình đang áp (dấu ✓). Stub mỗi card hiện
**QUY TẮC** chương trình (`10%` hoặc `500K/máy`), các dòng dưới liệt kê **điều kiện chương
trình THỰC SỰ có**: đơn tối thiểu, số lượng tối thiểu/dòng, trần giảm tối đa, ngân sách
(`usageLimit` = số đơn tối đa). **Promotion KHÔNG có khái niệm giới hạn mỗi khách** (khác
Voucher) nên card không hiện dòng đó. **Không** in số tiền tính ra cho giỏ này ở stub, vì đó
là kết quả chứ không phải định nghĩa chương trình (chương trình "10%" mà card ghi "giảm 3tr"
khiến khách hiểu nhầm mệnh giá). Tổng tiền giảm thực tế hiện ở footer overlay mục **"Tiết
kiệm được"**. Sidebar đơn hàng hiển thị dòng "Giảm giá sản phẩm" = `promotionDiscount`.

---

## 9. Edge case

| # | Tình huống | Xử lý |
|---|---|---|
| E-P01 | 2 chương trình cùng khớp 1 dòng | Chỉ áp cái `priority` cao hơn; hoà thì giảm nhiều hơn (BR-P09) |
| E-P02 | Chương trình `PERCENT` giảm > 100% | Cap ở 100% khi tính (`min(value, 100)`) |
| E-P03 | `AMOUNT` 500k/máy, mua 5 máy, cap 1tr | Giảm **1.000.000đ**, phần dư cắt dần (BR-P12) |
| E-P04 | Sản phẩm khớp scope nhưng nằm trong danh sách loại trừ | **Không giảm** — loại trừ thắng |
| E-P05 | `BRAND = "asus"` lưu vào, sản phẩm có `factory = "ASUS"` | Khớp — chuẩn hoá UPPERCASE + so không phân biệt hoa/thường (BR-P08) |
| E-P06 | Dòng vừa có giá flash vừa khớp promotion | Chỉ giá flash giảm, `lineDiscount = 0` (BR-P10) |
| E-P07 | Chương trình hết `usageLimit` | Đơn sau không được áp; nếu vừa hết lúc chốt song song → `PROMOTION_OUT_OF_STOCK`, rollback |
| E-P08 | Chương trình bị tắt sau khi đơn đã đặt | Đơn cũ **không đổi tiền** — snapshot `OrderDetail.discountAmount` + `promotionId` |
| E-P09 | `scopeType != ALL` nhưng `scopeValues` rỗng | Chặn lúc tạo (`PROMOTION_SCOPE_REQUIRED`) |
| E-P10 | Admin tạo chương trình với hình thức giảm ngoài `PERCENT`/`AMOUNT` | **Không thể** — enum chỉ có 2 giá trị, FE cũng chỉ cho chọn 2 |
| E-P11 | Một chương trình giảm 3 dòng trong 1 đơn | `usedCount` chỉ +1 (BR-P04) |
| E-P12 | Chương trình `AMOUNT` làm tổng giảm vượt `subtotal` | Cap `promotionDiscount ≤ subtotal`; `Cần thanh toán` không âm |
| E-P13 | Xoá sản phẩm đang nằm trong danh sách loại trừ | Không lỗi — `product_id` lưu dạng chuỗi, không FK |

---

## 10. Acceptance Criteria

| Mã | Tiêu chí |
|---|---|
| **AC-P01** | Tạo chương trình giảm 10% danh mục Laptop → thêm laptop vào giỏ → thấy giảm ngay ở giỏ và checkout |
| **AC-P02** | Chương trình hết hạn (`endDate` qua) → không áp |
| **AC-P03** | Chương trình loại trừ 1 sản phẩm → sản phẩm đó không giảm, các sản phẩm khác trong scope vẫn giảm |
| **AC-P04** | 2 chương trình cùng khớp → chỉ áp cái `priority` cao hơn |
| **AC-P05** | Đặt đơn → `usedCount` tăng **1 lần/đơn** dù chương trình khớp 3 dòng |
| **AC-P06** | `AMOUNT` 500k, mua 2 cùng sản phẩm → giảm đúng **1.000.000đ** |
| **AC-P07** | `AMOUNT` 500k/máy + cap 1tr, mua 5 máy → chỉ giảm **1.000.000đ** |
| **AC-P08** | `lineDiscount` **không bao giờ** > `lineTotal` |
| **AC-P09** | Chương trình đạt `usageLimit` → đơn sau không được áp |
| **AC-P10** | Tạo chương trình `scopeType = BRAND = ASUS` giảm 8% → thêm ASUS vào giỏ → tiền đổi ngay |
| **AC-P11** | Dòng đang có giá flash + khớp promotion → **chỉ** giá flash giảm, không cộng promotion |
| **AC-P12** | Huỷ đơn → `usedCount` của **từng** chương trình đã áp giảm đúng 1 |
| **AC-P13** | Form chỉ có 2 hình thức giảm (`PERCENT`/`AMOUNT`); gửi `discountType` khác → BE từ chối deserialize (enum chỉ có 2 giá trị) |
| **AC-P13b** | Gửi `type` khác `PRODUCT_DISCOUNT` → BE từ chối deserialize (enum chỉ có 1 giá trị) |
| **AC-P13c** | Tạo chương trình `type = null` → lỗi `INVALID_PROMOTION_CONFIG` |
| **AC-P14** | `scopeType != ALL` + danh sách rỗng → lỗi `PROMOTION_SCOPE_REQUIRED` |
| **AC-P15** | STAFF thấy menu "Khuyến mại", tạo/sửa được, **không** có nút Xoá (không có quyền). CUSTOMER gọi API admin → 403 |
| **AC-P16** | Ngừng áp chương trình → đơn cũ đã áp vẫn hiển thị đúng số tiền và tên chương trình |
| **AC-P17** | Số tiền promotion ở overlay khớp 100% số tiền ghi vào đơn sau khi đặt |

---

## 11. Truy vết

| BR | UC | AC | Code |
|---|---|---|---|
| BR-P01 | UC-P04 | — | `PromotionEngine.resolve` (hàm thuần) |
| BR-P02 | UC-P04, UC-P05 | AC-P17 | `CartService.toResponse` + `OrderService.createOrder` dùng chung engine |
| BR-P03 | UC-P05 | AC-P05, AC-P09 | `PromotionRepository.incrementUsedCount` (JPQL atomic) |
| BR-P04 | UC-P05 | AC-P05 | `promo.appliedPromotions()` distinct + `incrementUsedCount` |
| BR-P05 | UC-P01 | AC-P13, AC-P14 | `PromotionService.validate` |
| BR-P07 | UC-P03 | AC-P16 | `PromotionService.deactivate` |
| BR-P08 | UC-P01 | AC-P10 | `PromotionScope.normalizeTargetValue` |
| BR-P09 | UC-P04 | AC-P04 | `PromotionEngine.pickWinner` |
| BR-P10 | UC-P04 | AC-P11 | `PromotionEngine.resolve` (`line.hasFlash()`) |
| BR-P11 | UC-P04 | AC-P06 | `PromotionEngine.rawDiscount` case `AMOUNT` |
| BR-P12 | UC-P04 | AC-P07, AC-P08 | `PromotionEngine.resolve` (cap theo promotion) |
| BR-P14 | UC-P04 | — | `PromotionEngine.pickWinner` (bỏ qua `stackable`) |
| — | UC-P06 | AC-P12 | `OrderService.restorePromotions` |
