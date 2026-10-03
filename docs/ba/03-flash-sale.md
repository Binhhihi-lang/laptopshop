# 03 — FLASH SALE (Phiên giá sốc)

> Tài liệu nghiệp vụ · v1.0 · 2026-09-27
> Đọc kèm: [README.md](README.md) §4 (công thức tính tiền) · [02-promotion.md](02-promotion.md)

---

## 1. Mục tiêu & phạm vi

### 1.1 Mục tiêu

Cho phép admin mở **phiên bán giá sốc theo khung giờ ngắn** (thường 2–4 giờ). Điểm khác
biệt cốt lõi so với Promotion: **giá ưu đãi hiển thị NGAY trên thẻ sản phẩm** ở trang
chủ / danh sách / chi tiết — khách thấy giá sốc mà không cần mở giỏ. Phiên có **kho
riêng** và **đồng hồ đếm ngược công khai**.

### 1.2 Trong phạm vi

- Admin tạo/sửa/xoá phiên + chọn sản phẩm vào phiên, đặt giá flash và kho riêng.
- Bật/tắt công tắc phiên (tạm dừng / mở lại) ngay trên màn chi tiết.
- Khách xem phiên đang chạy (đếm ngược) và phiên sắp tới.
- Giá flash áp tự động trên thẻ sản phẩm, trong giỏ, và khi chốt đơn.
- Trừ/hoàn kho phiên chống bán vượt.
- Giới hạn số máy mỗi khách trong phiên.

### 1.3 Ngoài phạm vi

- **Lịch lặp tự động hằng ngày** (kiểu Shopee 9h/12h/19h) — v1 admin tạo từng phiên thủ công.
- Thông báo "Nhắc tôi" khi phiên sắp mở.
- Flash sale **không bao giờ sửa `Product.price`** — đây là ràng buộc cứng, không phải
  tính năng thiếu.

---

## 2. Actor

| Actor | Mô tả | Quyền |
|---|---|---|
| **Admin** | Quản trị viên | Toàn quyền flash sale |
| **Staff** | Nhân viên | Tạo/xem/sửa — **không xoá** |
| **Customer** | Khách mua hàng | Xem phiên, mua giá flash |
| **Hệ thống** | `FlashSaleService` | Nguồn giá flash duy nhất |

---

## 3. Use Case

### UC-F01 — Admin tạo phiên flash sale
**Actor:** Admin/Staff · **Tiền điều kiện:** `CREATE_FLASH_SALE`

**Luồng chính:**
1. Admin mở form tạo phiên.
2. Nhập: tên, mô tả, thời gian bắt đầu/kết thúc, công tắc `active`.
3. Thêm sản phẩm vào phiên (bảng inline): chọn sản phẩm (có tìm kiếm), giá flash, kho
   flash, giới hạn mỗi khách.
4. Hệ thống kiểm tra: tên không rỗng, `endAt > startAt`, có ít nhất 1 sản phẩm, không
   trùng sản phẩm, `flashPrice > 0`, `flashStock ≥ 0` (số CÒN LẠI), **`flashPrice < Product.price`**,
   **`perUserLimit` BẮT BUỘC (≥ 1) và `≤ flashStock`**.
5. Lưu phiên + items, `soldInFlash = 0`.

**Luồng phụ:**
- *Tên rỗng* → `FLASH_SALE_NAME_REQUIRED` (4302).
- *`endAt` ≤ `startAt`* → `INVALID_FLASH_SALE_DATE_RANGE` (4305).
- *Không có sản phẩm* → `FLASH_SALE_NO_ITEMS` (4306).
- *Sản phẩm trùng trong phiên* → `FLASH_SALE_ITEM_DUPLICATE` (4304).
- *`flashPrice` ≥ giá bán thường* → `FLASH_PRICE_NOT_LOWER` (4303).
- *`flashPrice` ≤ 0* → `INVALID_FLASH_PRICE` (4307); *`flashStock` < 0* → `INVALID_FLASH_STOCK` (4308).
- *`flashStock` > tồn kho thật* → `FLASH_STOCK_EXCEEDS_PRODUCT_STOCK` (4313).
- *`perUserLimit` để trống / < 1* → `FLASH_PER_USER_LIMIT_REQUIRED` (4315).
- *`perUserLimit` > `flashStock`* → `FLASH_PER_USER_LIMIT_EXCEEDS_STOCK` (4314).

> **BR-F01 — `flashPrice` BẮT BUỘC nhỏ hơn `Product.price`.** Flash sale mà không rẻ hơn
> giá thường thì không phải flash sale. Chặn cả ở tầng service lẫn tầng DB (unique index
> cho trùng sản phẩm).

---

### UC-F02 — Admin cập nhật phiên
**Actor:** Admin/Staff · **Tiền điều kiện:** `UPDATE_FLASH_SALE`

**Luồng chính:**
1. Admin sửa thông tin phiên / danh sách sản phẩm.
2. Với sản phẩm **đã có trong phiên**: hệ thống **giữ nguyên object cũ** → chỉ đổi giá /
   kho / giới hạn, **không mất `soldInFlash`** (số đã bán trong phiên).
3. Sản phẩm mới → tạo item mới. Sản phẩm bị bỏ khỏi danh sách → xoá cứng.

> **BR-F02 — Cập nhật phiên không được reset số đã bán.** Admin sửa giá flash giữa phiên
> mà `soldInFlash` về 0 thì kho ảo tăng vọt → bán vượt.

---

### UC-F03 — Admin gỡ / tạm dừng phiên
**Actor:** Admin/Staff

- **Gỡ phiên** (`DELETE`, quyền `DELETE_FLASH_SALE`): set `active = false` **RỒI** xoá mềm.
- **Tạm dừng / Mở lại** (`PATCH /{id}/status`): đổi công tắc `active`.

> **BR-F03 — Gỡ phiên phải set `active = false` TRƯỚC khi xoá mềm.** `resolvePriceMap`
> không lọc được `deleted_at` của bảng cha qua join → **`active` mới là điều kiện chặn thật**.

> **Tắt công tắc = tắt NGAY** cả khi còn trong khung giờ. Giá flash biến mất khỏi thẻ sản
> phẩm, **không chạm giá gốc** (BR-F04).

---

### UC-F04 — Hệ thống cung cấp giá flash
**Actor:** Hệ thống · **Hàm:** `FlashSaleService.resolvePriceMap(productIds, now)`

**Luồng chính:**
1. Một câu query duy nhất: join `flash_sale_item` + `flash_sale`, lọc `product.id IN ids`
   + `active = true` + `now BETWEEN startAt AND endAt`.
2. Bỏ qua item đã hết kho (`flashStock <= 0` — flashStock là số còn lại).
3. Trả về `Map<productId, FlashPriceView>`.

**Bản cho giỏ/chốt đơn** nhận thêm `userId` → gắn `perUserLimitLeft` theo đúng khách.

> **BR-F05 — MỘT hàm duy nhất cho mọi nơi đọc giá flash.** Card sản phẩm, giỏ hàng, trang
> `/flash-sale`, và chốt đơn đều gọi `resolvePriceMap`. Nhờ vậy giá trên card ≡ giá trong
> giỏ ≡ giá trong đơn. Không viết logic "tìm phiên đang chạy" rải rác.

> **BR-F06 — Một query `IN` theo danh sách id, không loop.** Tránh N+1 khi list sản phẩm.

---

### UC-F05 — Khách xem phiên flash sale
**Actor:** Customer · **Endpoint:** `GET /client/flash-sales/*` — **public, không cần login**

| Endpoint | Trả về |
|---|---|
| `/active` | Phiên **đang chạy** (kết thúc sớm nhất trước); `null` nếu không có |
| `/upcoming?limit=5` | Danh sách phiên **sắp tới** — FE vẽ "Diễn ra lúc 19:00" |
| `/{id}` | Chi tiết một phiên |

**FE hiển thị:**
- Trang chủ: dải flash sale với **đồng hồ đếm ngược** `HH:MM:SS` + thanh tiến độ "Đã bán x/y".
- Thẻ sản phẩm: giá flash (màu nhấn) + giá thường gạch ngang + badge `FLASH SALE` + % giảm.
- Trang `/flash-sale`: header phiên + countdown + grid sản phẩm flash + tab "Phiên sắp diễn ra".

---

### UC-F06 — Khách đặt hàng có sản phẩm flash
**Actor:** Customer · **Endpoint:** `POST /client/orders`

**Luồng chính:**
1. Hệ thống dựng đơn từ giỏ server.
2. **Tính lại giá flash NGAY LÚC CHỐT** (`resolvePriceMap(ids, userId, now)`).
3. Với mỗi dòng: giá bán = `flashPrice` nếu còn hiệu lực **VÀ** khách chưa hết suất của
   mình; ngược lại giá thường.
4. **Trừ kho phiên atomic**: `UPDATE ... SET flashStock -= qty, soldInFlash += qty WHERE flashStock >= qty`.
5. Nếu trừ thành công → lưu `OrderDetail.flashSaleItemId` (để hủy đơn hoàn đúng suất).
6. Snapshot giá vào `OrderDetail.price`. `OrderDetail.promotionId = null` (flash không
   phải promotion).

**Luồng phụ:**
- *Kho phiên cạn giữa lúc chốt* → dòng đó **âm thầm về giá thường**, **KHÔNG fail đơn**
  (D27). Đây là fallback có chủ ý.
- *Khách đã hết suất cá nhân* → dòng về giá thường, không chặn đơn.

> **BR-F07 — Tính lại giá tại thời điểm chốt.** Giá ở giỏ chỉ là preview; tới lúc chốt mới
> là giá thật. Khách thêm hàng lúc còn flash, tới checkout hết phiên → dùng giá thường và
> FE toast "Ưu đãi đã kết thúc". **Không chặn đặt hàng.**

> **BR-F08 — Trừ kho phiên atomic.** Câu `UPDATE` chứa điều kiện `soldInFlash + qty <=
> flashStock` nên DB tự chặn; 2 khách chốt máy cuối song song thì đúng 1 câu có tác dụng.
> 0 dòng = kho cạn → fallback giá thường, **không phải lỗi chặn**.

> **BR-F09 — `Product.price` KHÔNG BAO GIỜ bị flash sale sửa.** Giá flash chỉ sống ở
> `FlashSaleItem.flashPrice`. Hết phiên = tự về giá cũ, không cần job khôi phục, không sợ
> quên restore.

---

### UC-F07 — Hoàn kho phiên khi huỷ đơn
**Actor:** Customer / Admin / Job

- Với mỗi dòng có `flashSaleItemId` → `soldInFlash` − qty (atomic, chặn dưới 0).
- Cùng transaction với việc đổi trạng thái đơn.
- Chi tiết ở [01-voucher.md](01-voucher.md) UC-V09.

> **BR-F10 — Vì sao lưu `flashSaleItemId` vào dòng đơn?** Trước V5 thông tin này không
> được lưu nên huỷ đơn làm **mất suất vĩnh viễn** trong phiên. Cột này là ảnh chụp id lúc
> chốt đơn; nếu item đã bị xoá cứng thì `releaseStock` chạy 0 dòng — vốn đã là no-op an toàn.

---

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| **BR-F01** | `flashPrice` phải **< `Product.price`** khi thêm vào phiên |
| **BR-F02** | Cập nhật phiên **không reset** `soldInFlash` cho sản phẩm đã có |
| **BR-F03** | Gỡ phiên = set `active = false` **trước** khi xoá mềm |
| **BR-F04** | `Product.price` **không bao giờ** bị flash sale sửa |
| **BR-F05** | Một hàm `resolvePriceMap` duy nhất cho card / giỏ / chốt đơn |
| **BR-F06** | Một query `IN` theo danh sách id — tránh N+1 |
| **BR-F07** | Tính lại giá flash **tại thời điểm chốt**; hết phiên → giá thường, không chặn mua |
| **BR-F08** | Trừ kho phiên **atomic**; cạn → fallback giá thường, không fail đơn |
| **BR-F09** | Dòng đã có giá flash → promotion **bỏ qua** (flash thắng) |
| **BR-F10** | Huỷ đơn hoàn **đúng suất** vào phiên qua `flashSaleItemId` |
| **BR-F11** | Hết suất cá nhân (`perUserLimit`) **ở bước chốt đơn** → dòng về giá thường, không chặn đơn (khách đã lỡ đặt) |
| **BR-F12** | Một sản phẩm chỉ xuất hiện **một lần** trong một phiên (unique index DB) |
| **BR-F13** | Khi `consumeStock` thất bại (kho phiên cạn giữa lúc chốt) → dòng về giá thường **VÀ phải tính lại toàn bộ tiền** (`promotionDiscount`, `eligibleAmount`, `voucherDiscount`, `totalPrice`). Không được để lại phần giảm của dòng đã mất flash |
| **BR-F14** | Đếm `perUserLimit` gộp theo **phiên**, không theo từng sản phẩm — 1 query cho cả phiên, không N+1 |
| **BR-F15** | `perUserLimit` **không được vượt `flashStock`** khi lưu phiên (4314). Suất 3 mà cho 1 khách mua tới 5 là con số ảo — cả phiên chỉ có 3 máy |
| **BR-F16** | FE phải **nói rõ trần mỗi khách** cho khách hàng: card/chi tiết sản phẩm hiện "Tối đa N máy/khách"; giỏ hiện dòng cảnh báo khi khách đã hết suất (`flashLimitReached`) |
| **BR-F17** | **Trần mỗi khách là BẮT BUỘC** — không còn giá trị "vô hạn" (V16). Để trống → chặn `FLASH_PER_USER_LIMIT_REQUIRED` (4315) |
| **BR-F18** | Thao tác **GIỎ** (thêm / đổi số lượng / gộp giỏ guest) vượt trần mỗi khách → **chặn ngay** `FLASH_PER_USER_LIMIT_EXCEEDED` (4316), không đợi tới lúc chốt đơn |

> **BR-F15 — Vì sao chặn `perUserLimit > flashStock`?** Chốt 2026-10-03. `perUserLimit`
> là trần của MỘT khách, `flashStock` là tổng suất cả phiên. Trần vượt tổng suất thì phần
> vượt không bao giờ chạm tới (kho cạn trước) → admin nhập số vô nghĩa. Chặn ngay lúc lưu,
> báo lỗi **từng dòng** trên form (không gộp 1 câu chung). Để trống = không giới hạn, luôn hợp lệ.

> **BR-F17 — Vì sao bỏ hẳn "vô hạn"?** Chốt 2026-10-03. `perUserLimit = null` nghĩa là MỘT
> khách ôm hết suất giá sốc: phiên còn 3 suất mà không đặt trần thì khách đầu tiên lấy cả 3,
> khách sau không còn suất nào. Đây là lỗ hổng nghiệp vụ, không phải tính năng. Nay trần bắt
> buộc ≥ 1 và ≤ suất còn lại; cột DB siết `NOT NULL`, dữ liệu cũ để trống quy về 1 (an toàn nhất).

> **BR-F18 — Vì sao chặn ở GIỎ mà không chỉ ở lúc chốt?** Chốt 2026-10-03. Trước đây giỏ chỉ
> kiểm tồn kho: khách đặt 3 máy khi phiên cho tối đa 1 vẫn lưu được, dòng lặng lẽ rơi về giá
> thường rồi khách mới ngỡ ngàng. Nay chặn ngay ở thao tác giỏ (nút `+` cũng bị khoá tại trần),
> nên khách không bao giờ rơi vào trạng thái khó hiểu. Chốt đơn vẫn giữ BR-F11 vì đơn có thể
> được tạo từ dữ liệu cũ (giỏ guest nhồi trước khi đăng nhập, phiên vừa đổi trần).

> **BR-F16 — Vì sao phải nói rõ trần cho khách?** Chốt 2026-10-03. Trước đây khách đã dùng
> hết suất thì dòng **âm thầm** về giá thường: giá tự dưng tăng so với lúc xem trên thẻ mà
> không hiểu vì sao. Nay BE gắn cờ `flashLimitReached` + trả `flashPerUserLimit`, FE hiện
> chữ "Bạn đã dùng hết suất giá sốc của phiên — dòng này tính giá thường (tối đa N máy/khách)".

> **BR-F11 — Vì sao về giá thường mà không chặn?** Chốt 2026-09-25: đồng nhất với BR-F07.
> Khách vẫn mua được hàng ở giá thường — chặn đơn vì hết suất khuyến mại là trải nghiệm xấu.
> Giỏ 3 máy mà suất còn 1 → **cả dòng** về giá thường (một dòng một giá).

> **Hệ quả đã biết:** mã lỗi `FLASH_PER_USER_LIMIT_REACHED` (4310) trở thành **mã chết** —
> không còn đường nào ném ra.

---

## 5. Luồng nghiệp vụ

```
┌─ ADMIN ───────────────────────────────────────────────────────────────┐
│  Tạo phiên: tên + ảnh + startAt..endAt + công tắc                     │
│  Thêm sản phẩm: flashPrice (< giá thường) + flashStock + perUserLimit │
│                        │                                              │
│                        ▼                                              │
│  Tạm dừng / Mở lại (công tắc)  ·  Gỡ phiên (active=false → xoá mềm)   │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
┌─ HỆ THỐNG (nguồn giá) ─▼──────────────────────────────────────────────┐
│  resolvePriceMap(productIds, now)  ← 1 query IN, không N+1            │
│      │  lọc: active + now ∈ [startAt, endAt] + còn flashStock         │
│      ▼                                                                │
│  Map<productId, FlashPriceView{flashPrice, flashStock, soldInFlash,   │
│                                 flashSaleId, endAt, perUserLimitLeft}>│
│      │                                                                │
│      ├──→ ProductService (card sản phẩm: giá flash + đã bán x/y)      │
│      ├──→ CartService (giá dòng giỏ)                                  │
│      ├──→ ClientFlashSaleController (trang /flash-sale)               │
│      └──→ OrderService.createOrder (GIÁ THẬT lúc chốt — D27)          │
│                    │                                                  │
│                    ▼                                                  │
│           consumeStock atomic (D29)                                   │
│                    │                                                  │
│         ┌──────────┴──────────┐                                       │
│      thành công            cạn kho                                     │
│         │                     │                                       │
│    lưu flashSaleItemId    fallback giá thường, KHÔNG fail đơn          │
└───────────────────────────────────────────────────────────────────────┘
                         │
                         ▼
              Huỷ đơn → releaseStock (hoàn đúng suất)
```

---

## 6. Data Dictionary

### 6.1 Bảng `flash_sales` — phiên (entity `FlashSale`)

| Cột | Kiểu | Null | Ý nghĩa |
|---|---|:--:|---|
| `id` | varchar(255) | ✗ | Khoá chính (UUID) |
| `name` | varchar(255) | ✗ | Tên phiên |
| `description` | varchar(255) | ✓ | Mô tả |
| `start_at` | datetime(6) | ✗ | Mở phiên |
| `end_at` | datetime(6) | ✗ | Đóng phiên |
| `active` | bit(1) | ✗ | Công tắc khẩn cấp — vẫn phải trong khung giờ mới có tác dụng |
| `created_at` / `updated_at` | datetime(6) | ✓ | Audit |
| `deleted_at` | datetime(6) | ✓ | Xoá mềm |

**Index:** `IDX_flash_sales_active_period (active, start_at, end_at)`.

### 6.2 Bảng `flash_sale_items` — sản phẩm trong phiên (entity `FlashSaleItem`)

| Cột | Kiểu | Null | Ý nghĩa |
|---|---|:--:|---|
| `id` | varchar(255) | ✗ | Khoá chính |
| `flash_sale_id` | varchar(255) | ✗ | FK → `flash_sales.id` |
| `product_id` | varchar(255) | ✗ | FK → `products.id` |
| `flash_price` | bigint | ✗ | Giá sốc — phải < `Product.price` |
| `flash_stock` | int | ✗ | Số suất **CÒN LẠI** của phiên (sống, tự giảm khi bán) |
| `sold_in_flash` | int | ✗ | Số đã bán trong phiên — **chỉ UPDATE atomic** |
| `per_user_limit` | int | ✗ | Tối đa mỗi khách trong phiên — **BẮT BUỘC, ≥ 1** (V16) |

**Ràng buộc:** `UNIQUE (flash_sale_id, product_id)` — chống thêm trùng sản phẩm.

> **Vì sao `flash_sale_items` KHÔNG có `deleted_at`?** Item chỉ có nghĩa trong phiên cha.
> Nếu xoá mềm thì `resolvePriceMap` vẫn trả nó về → khách mua được giá sốc của sản phẩm
> admin đã gỡ. **Xoá item là xoá cứng.**

### 6.3 Cột liên quan trên bảng khác

| Bảng | Cột | Ý nghĩa |
|---|---|---|
| `order_detail` | `flash_sale_item_id` | Item đã trừ kho lúc chốt — huỷ đơn hoàn đúng suất. **Cố ý không FK** (item xoá cứng) |
| `order_detail` | `price` | Giá bán thật tại thời điểm mua (**đã gồm giá flash**) |
| `product` | `price` | **Không bao giờ** bị flash sale sửa |

---

## 7. API Contract

### 7.1 Admin — `/api/v1/admin/flash-sales`

| Method | Path | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/flash-sales` | `READ_FLASH_SALE` | — | `List<FlashSaleResponse>` |
| GET | `/flash-sales/{id}` | `READ_FLASH_SALE` | — | `FlashSaleResponse` |
| POST | `/flash-sales` | `CREATE_FLASH_SALE` | `@ModelAttribute FlashSaleCreationRequest` (form-data) | `FlashSaleResponse` |
| PUT | `/flash-sales/{id}` | `UPDATE_FLASH_SALE` | `@ModelAttribute FlashSaleCreationRequest` | `FlashSaleResponse` |
| DELETE | `/flash-sales/{id}` | `DELETE_FLASH_SALE` | — | — |
| PATCH | `/flash-sales/{id}/status` | `UPDATE_FLASH_SALE` | `{ active }` | `FlashSaleResponse` |

**Vì sao form-data?** DTO đóng gói 1 part JSON `flashSaleInfo` (khuôn Product).

**`FlashSaleCreationRequest`:**

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:--:|---|
| `name` | String | ✓ | |
| `description` | String | | |
| `startAt` | LocalDateTime | ✓ | |
| `endAt` | LocalDateTime | ✓ | |
| `active` | Boolean | | Mặc định true |
| `imageUrl` | String | | Dán link ảnh |
| `removeImage` | boolean | | |
| `items` | List\<FlashSaleItemRequest\> | ✓ | Ít nhất 1 |

**`FlashSaleItemRequest`:** `{ productId (✓), flashPrice (✓), flashStock (✓), perUserLimit }`.

**`FlashSaleResponse`:** `id, name, description, startAt, endAt, active,
running, itemCount, items[], createdAt, updatedAt`.

**`FlashSaleItemResponse`:** `id, productId, productCode, productName, productImage,
flashPrice, regularPrice, flashStock, soldInFlash, remainingStock, perUserLimit`.

> `running` là trường **tính toán** = `active && now ∈ [startAt, endAt]` — FE dùng để vẽ
> badge "Đang chạy / Sắp diễn ra / Đã kết thúc".

---

### 7.2 Client — `/api/v1/client/flash-sales` (**public**)

| Method | Path | Quyền | Trả về |
|---|---|---|---|
| GET | `/flash-sales/active` | permitAll | `FlashSaleResponse` hoặc `null` |
| GET | `/flash-sales/upcoming?limit=5` | permitAll | `List<FlashSaleResponse>` |
| GET | `/flash-sales/{id}` | permitAll | `FlashSaleResponse` |

**Giá flash cũng được trả kèm trong `ProductResponse` và `CartItemResponse`:**

| Trường | Ý nghĩa |
|---|---|
| `flashPrice` | Giá sốc nếu đang trong phiên |
| `flashStock` / `flashSold` | Suất còn lại / đã bán — FE vẽ "Đã bán x/y" |
| `flashSaleId` | Id phiên |
| `flashEndAt` | Thời điểm kết thúc — FE đếm ngược |

> **Bắt buộc có các trường này trên `ProductResponse`:** card sản phẩm phải hiển thị giá
> flash **mà không gọi thêm API**.

---

### 7.3 Bảng mã lỗi

| Code | Hằng số | HTTP | Thông báo |
|---|---|---|---|
| 4301 | `FLASH_SALE_NOT_FOUND` | 404 | Không tìm thấy phiên flash sale |
| 4302 | `FLASH_SALE_NAME_REQUIRED` | 400 | Tên phiên flash sale không được để trống |
| 4303 | `FLASH_PRICE_NOT_LOWER` | 400 | Giá flash sale phải thấp hơn giá bán hiện tại của sản phẩm |
| 4304 | `FLASH_SALE_ITEM_DUPLICATE` | 400 | Sản phẩm này đã có trong phiên flash sale |
| 4305 | `INVALID_FLASH_SALE_DATE_RANGE` | 400 | Thời gian kết thúc phiên phải sau thời gian bắt đầu |
| 4306 | `FLASH_SALE_NO_ITEMS` | 400 | Phiên flash sale phải có ít nhất một sản phẩm |
| 4307 | `INVALID_FLASH_PRICE` | 400 | Giá flash sale phải lớn hơn 0 |
| 4308 | `INVALID_FLASH_STOCK` | 400 | Số lượng flash sale phải lớn hơn 0 |
| 4309 | `FLASH_STOCK_EXHAUSTED` | 400 | Sản phẩm đã bán hết số lượng dành cho flash sale — **mã chết**: cạn kho → về giá thường, không ném lỗi (BR-F08) |
| 4310 | `FLASH_PER_USER_LIMIT_REACHED` | 400 | Bạn đã mua đủ số lượng tối đa cho sản phẩm flash sale này — **mã chết**, xem BR-F11 |
| 4311 | `FLASH_SALE_PRODUCT_NOT_FOUND` | 404 | Sản phẩm đưa vào phiên không tồn tại |
| 4312 | `FLASH_SALE_OVERLAP` | 400 | Khung giờ của phiên bị trùng với một phiên khác cùng sản phẩm |
| 4313 | `FLASH_STOCK_EXCEEDS_PRODUCT_STOCK` | 400 | Kho số lượng flash không được vượt tồn kho hiện tại của sản phẩm |
| 4314 | `FLASH_PER_USER_LIMIT_EXCEEDS_STOCK` | 400 | Giới hạn mỗi khách không được vượt số suất còn lại của phiên |
| 4315 | `FLASH_PER_USER_LIMIT_REQUIRED` | 400 | Vui lòng nhập số máy tối đa mỗi khách được mua trong phiên |
| 4316 | `FLASH_PER_USER_LIMIT_EXCEEDED` | 400 | Bạn chỉ được mua tối đa số máy cho phép của phiên giá sốc |

---

## 8. Màn hình & tương tác

### 8.1 Admin

| Màn | Nội dung |
|---|---|
| **Danh sách Flash Sale** (`/admin/flash-sales`) | Bảng + badge trạng thái (`Sắp diễn ra` / `Đang chạy` / `Đã kết thúc` tính từ `startAt`/`endAt` + `active`) + bulk + kebab |
| **Form Flash Sale** (`/admin/flash-sales/create`, `/:id/edit`) | Tên, mô tả, `startAt`/`endAt`, `active`; **bảng item thêm/xoá inline**: chọn sản phẩm (searchable), `flashPrice`, `flashStock`, `perUserLimit`; **validate client `flashPrice < giá thường`** + hiển thị % giảm thời gian thực |
| **Chi tiết Flash Sale** (`/admin/flash-sales/:id`) | KPI 4 thẻ, thông tin, timeline, **bảng sản phẩm trong phiên**, nút "Tạm dừng" / "Mở lại" |

### 8.2 Client

| Thành phần | Nội dung |
|---|---|
| **`flash-sale-strip`** (trang chủ) | Icon tia sét + tên phiên + **đồng hồ đếm ngược** `HH:MM:SS` (tick `setInterval`, cleanup `DestroyRef`; qua `endAt` → tự ẩn + refetch) + thanh tiến độ "Đã bán x/y" + link "Xem tất cả" → `/flash-sale` |
| **`product-card`** | Giá flash (màu `rose-600`, to) + giá thường gạch ngang + badge `FLASH SALE` + % giảm + progress bar nhỏ "Đã bán x/y" (đỏ dần khi sắp hết). **Tương thích ngược:** không có `flashPrice` → render y như cũ |
| **Trang `/flash-sale`** | Header phiên + countdown, grid sản phẩm flash, tab "Phiên sắp diễn ra" (đếm ngược tới `startAt`). Route **public** |

> **Tương thích ngược bắt buộc:** `product-card` và `order-summary` là component dùng chung
> nhiều nơi. Input mới phải **optional** — thiếu thì render y như trước, không được vỡ trang
> danh sách / chi tiết đơn cũ.

---

## 9. Edge case

| # | Tình huống | Xử lý |
|---|---|---|
| E-F01 | `flashPrice >= Product.price` khi lưu | Chặn `FLASH_PRICE_NOT_LOWER` (BR-F01) |
| E-F02 | Hết phiên giữa 2 lần refresh | Card về giá thường ngay (không cache sai) |
| E-F03 | 2 khách mua máy cuối (`flashStock = 1`) song song | 1 thành công giá flash, 1 vẫn đặt được **giá thường**; `flashStock` không xuống dưới 0 (BR-F08) |
| E-F04 | Khách thêm hàng flash vào giỏ, tới chốt thì hết phiên | Tính lại lúc chốt → giá thường + toast "Ưu đãi đã kết thúc"; **không** chặn đơn (BR-F07) |
| E-F05 | `perUserLimit = 1`, khách bấm `+` lên 3 máy trong giỏ | **Chặn ngay ở giỏ** `FLASH_PER_USER_LIMIT_EXCEEDED` (4316) + nút `+` khoá tại trần (BR-F18) |
| E-F06 | Sản phẩm vừa có flash vừa khớp promotion | **Chỉ** giá flash giảm, promotion `lineDiscount = 0` (BR-F09) |
| E-F07 | Admin sửa giá flash giữa phiên đang chạy | `soldInFlash` giữ nguyên (BR-F02) |
| E-F08 | Admin gỡ phiên | `active = false` trước, rồi xoá mềm — nếu không, giá flash vẫn sống (BR-F03) |
| E-F09 | Admin xoá sản phẩm khỏi phiên | Xoá **cứng** item (bảng không có `deleted_at`) |
| E-F10 | Thêm cùng sản phẩm 2 lần vào 1 phiên | Chặn `FLASH_SALE_ITEM_DUPLICATE` + unique index DB (BR-F12) |
| E-F11 | Huỷ đơn flash sau khi admin đã gỡ phiên | `releaseStock` chạy 0 dòng — no-op an toàn (item đã xoá cứng) |
| E-F12 | Giỏ có 3 máy, suất cá nhân còn 1 | **Cả dòng** về giá thường (một dòng một giá) |
| E-F13 | Hết kho phiên giữa chừng | Dòng đó về giá thường, **không chặn mua** (BR-F08) |
| E-F14 | Dòng flash cạn kho ngay lúc chốt, mà dòng đó **đang được promotion giảm** | **Tính lại toàn bộ tiền** (BR-F13): dòng mất flash thì promotion được áp lại trên giá thường, `eligibleAmount` và `voucherDiscount` tính lại theo giá mới. Nếu không, đơn ghi giảm tiền mà không dòng nào có `promotionId` → sổ admin không khớp và khách trả thiếu |
| E-F15 | Admin đặt `tối đa/khách` lớn hơn `suất còn lại` | Chặn `FLASH_PER_USER_LIMIT_EXCEEDS_STOCK` (4314) + FE báo lỗi ngay **dưới ô của dòng đó** (BR-F15) |
| E-F16 | Khách đã dùng hết suất, mở giỏ hàng | Dòng về giá thường **kèm chữ giải thích** (BR-F16) |
| E-F17 | Admin để trống `tối đa/khách` | Chặn `FLASH_PER_USER_LIMIT_REQUIRED` (4315) + FE báo lỗi dưới ô đó (BR-F17) |
| E-F18 | Giỏ guest (localStorage) nhồi 5 máy, đăng nhập khi phiên cho tối đa 1 | Lúc gộp giỏ **KẸP** về 1 thay vì ném lỗi (không làm hỏng cả thao tác merge) — BR-F18 |

---

## 10. Acceptance Criteria

| Mã | Tiêu chí |
|---|---|
| **AC-F01** | Tạo phiên 9:00–11:00 + item giá flash < giá thường → card sản phẩm ở list/detail **hiển thị giá flash gạch ngang giá thường** không cần mở giỏ |
| **AC-F02** | Lưu `flashPrice >= price` → chặn `FLASH_PRICE_NOT_LOWER` |
| **AC-F03** | Hết phiên giữa 2 lần refresh → card về giá thường ngay |
| **AC-F04** | 2 khách mua máy cuối song song → 1 giá flash, 1 giá thường; `flashStock` không âm |
| **AC-F05** | `perUserLimit = 1`: mua lần 2 → giá thường, không lỗi chặn |
| **AC-F06** | Flash + promotion cùng khớp 1 dòng → **chỉ** giá flash giảm, không cộng promotion |
| **AC-F07** | Tắt công tắc phiên giữa khung giờ → giá flash biến mất khỏi card ngay, `Product.price` không đổi |
| **AC-F08** | Hết phiên → `Product.price` **không đổi** (không cần job khôi phục) |
| **AC-F09** | Gỡ phiên → `resolvePriceMap` không trả giá flash nữa |
| **AC-F10** | Huỷ đơn flash → `soldInFlash` hoàn đúng số lượng |
| **AC-F11** | Sửa phiên (đổi giá flash) khi đang chạy → `soldInFlash` giữ nguyên, không reset về 0 |
| **AC-F12** | Thêm trùng sản phẩm vào phiên → lỗi `FLASH_SALE_ITEM_DUPLICATE` |
| **AC-F13** | Phiên không có sản phẩm → lỗi `FLASH_SALE_NO_ITEMS` |
| **AC-F14** | `GET /client/flash-sales/active` không cần token; trả `null` khi không có phiên |
| **AC-F15** | `GET /client/flash-sales/upcoming` trả danh sách theo `startAt` tăng dần |
| **AC-F16** | Countdown tick đúng; mở tab khác không leak interval; qua `endAt` → strip tự ẩn |
| **AC-F17** | `product-card` không có `flashPrice` → render **y như cũ** (không hồi quy) |
| **AC-F18** | Home không có banner/flash nào (DB trống) → về đúng hero cũ hoặc ẩn khối, không vỡ layout |
| **AC-F19** | STAFF tạo phiên + thêm item → home client thấy ngay sau khi refresh |
| **AC-F20** | STAFF không có nút Xoá phiên; CUSTOMER gọi API admin → 403 |
| **AC-F21** | `suất còn lại = 3`, `tối đa/khách = 5` → lưu bị chặn `FLASH_PER_USER_LIMIT_EXCEEDS_STOCK`; FE hiện lỗi dưới ô dòng đó |
| **AC-F22** | `suất còn lại = 3`, `tối đa/khách = 3` → lưu thành công; để trống → thành công |
| **AC-F23** | Khách đã mua đủ suất mở giỏ → dòng hiện giá thường kèm dòng chữ giải thích trần mỗi khách |
| **AC-F24** | Admin để trống `tối đa/khách` → lưu bị chặn `FLASH_PER_USER_LIMIT_REQUIRED`; FE hiện "Bắt buộc nhập" dưới ô đó |
| **AC-F25** | Phiên cho tối đa 1/khách: khách bấm `+` lên 2 → nút bị khoá, hiện toast "Chỉ được mua tối đa 1 máy/khách"; gọi API thẳng cũng bị 4316 |
| **AC-F26** | Khách chưa mua gì (giỏ trống) thêm 2 máy khi trần = 1 → chặn ngay, không lưu giỏ |

---

## 11. Truy vết

| BR | UC | AC | Code |
|---|---|---|---|
| BR-F01 | UC-F01 | AC-F02 | `FlashSaleService.validateFlashPriceBelowSellingPrice` |
| BR-F02 | UC-F02 | AC-F11 | `FlashSaleService.update` (giữ object cũ) |
| BR-F03 | UC-F03 | AC-F09 | `FlashSaleService.deleteFlashSale` |
| BR-F04 | UC-F06 | AC-F07, AC-F08 | `Product.price` bất biến |
| BR-F05 | UC-F04 | AC-F01 | `FlashSaleService.resolvePriceMap` |
| BR-F06 | UC-F04 | — | `FlashSaleItemRepository.findCurrentByProductIds` |
| BR-F07 | UC-F06 | AC-F04 | `OrderService.createOrder` (resolve lại) |
| BR-F08 | UC-F06 | AC-F04 | `FlashSaleItemRepository.consumeStock` |
| BR-F09 | UC-F06 | AC-F06 | `PromotionEngine.resolve` (`line.hasFlash()`) |
| BR-F10 | UC-F07 | AC-F10 | `OrderService.restorePromotions` + `releaseStock` |
| BR-F11 | UC-F06 | AC-F05 | `FlashPriceView.allowsFlashFor` |
| BR-F12 | UC-F01 | AC-F12 | unique index `UK_flash_sale_items_sale_product` |
| BR-F15 | UC-F01 | AC-F21, AC-F22 | `FlashSaleService.validateItemFields` + `limitWithinStockValidator` (FE) |
| BR-F16 | UC-F06 | AC-F23 | `CartService.applyFlashPrices` (cờ `flashLimitReached`) + `cart-line-item` |
| BR-F17 | UC-F01 | AC-F24 | `FlashSaleService.validateItemFields` + `FlashSaleItem.perUserLimit` (NOT NULL) |
| BR-F18 | UC-F06 | AC-F25, AC-F26 | `CartService.enforceFlashPerUserLimit` + `cart-line-item.maxQty` |
