# 01 — VOUCHER (Phiếu giảm giá)

> Tài liệu nghiệp vụ · v1.0 · 2026-09-27
> Đọc kèm: [README.md](README.md) §4 (công thức tính tiền) · [02-promotion.md](02-promotion.md)

---

## 1. Mục tiêu & phạm vi

### 1.1 Mục tiêu

Cho phép admin tạo **phiếu giảm giá** và khách **sở hữu** phiếu đó trong ví, dùng để
giảm tiền khi thanh toán. Voucher giảm ở **cấp đơn hàng** — trừ **một lần** trên toàn
đơn (hoặc trên phần tiền thuộc phạm vi), khác với Promotion giảm theo từng dòng.

### 1.2 Trong phạm vi

- Admin tạo/sửa/xoá mềm mẫu voucher, bật/tắt hàng loạt, phát voucher đích danh cho khách.
- Cấu hình: mức giảm (% hoặc số tiền), thời gian hiệu lực, phạm vi áp dụng (nhiều giá
  trị), đơn tối thiểu, trần giảm, giới hạn lượt toàn hệ thống, giới hạn lượt mỗi khách.
- Khách: xem kho voucher có thể nhận, bấm "Lưu mã" để đưa vào ví, xem ví, **chọn voucher
  từ ví** khi thanh toán.
- Kiểm tra mã ở giỏ hàng (preview) khớp 100% với số tiền ghi vào đơn.
- Hoàn voucher về ví khi đơn bị huỷ.

### 1.3 Ngoài phạm vi

- Nhiều voucher trên cùng một đơn (v1: **tối đa 1**).
- Voucher tự sinh khi đơn hoàn tất (`PromotionType.GIFT_VOUCHER`) — **đã xoá** cùng đợt dọn enum 2026-09-28 (xem [02-promotion.md](02-promotion.md) §1.3).
- Tặng voucher theo sinh nhật / đăng ký mới (`UserVoucherSource.WELCOME`/`BIRTHDAY` có
  trong enum nhưng **chưa có luồng nào sinh ra**).

> **Cập nhật 2026-09-28 — `VoucherType.GIFT` đã bị XOÁ.** Trước đây có 3 kiểu phát hành
> (`PUBLIC`/`ASSIGNED`/`GIFT`) nhưng `GIFT` **không có hành vi riêng**: `claim()` chặn
> ASSIGNED và GIFT y hệt nhau, còn `assignToUsers()` phục vụ cả hai và ra cùng kết quả. UI
> hiển thị 2 lựa chọn giống nhau. Đã xoá khỏi enum Java + siết enum DB (migration **V11**,
> dòng `GIFT` cũ chuyển thành `ASSIGNED`) + xoá khỏi FE + mockup.
>
> **KHÔNG đụng `UserVoucherSource.GIFTED`** — giá trị đó là **NGUỒN vào ví** (khác khái
> niệm với `voucher_type` là **KIỂU phát hành**) và vẫn đang dùng thật.

---

## 2. Actor

| Actor | Mô tả | Quyền liên quan |
|---|---|---|
| **Admin** | Quản trị viên | Toàn quyền voucher |
| **Staff** | Nhân viên | Tạo/xem/sửa, **không xoá** |
| **Customer** | Khách mua hàng | Nhận voucher, xem ví, dùng khi đặt hàng |
| **Hệ thống** | Job dọn định kỳ | Chuyển voucher quá hạn sang `EXPIRED` |

---

## 3. Use Case

### UC-V01 — Admin tạo mẫu voucher
**Actor:** Admin/Staff · **Tiền điều kiện:** có `CREATE_VOUCHER`

**Luồng chính:**
1. Admin mở form tạo voucher.
2. Nhập: mã (code), tiêu đề, mô tả, mức giảm (chọn **một trong hai**: phần trăm HOẶC
   số tiền), thời gian hiệu lực, lượt dùng, phạm vi, điều kiện.
3. Hệ thống kiểm tra: mã chưa tồn tại, cấu hình giảm giá hợp lệ, phạm vi hợp lệ.
4. Hệ thống chuẩn hoá mã (viết hoa, cắt khoảng trắng), lưu voucher, `usedCount = 0`.

**Luồng phụ:**
- *Mã đã tồn tại* → lỗi `VOUCHER_ALREADY_EXISTS` (4002).
- *Điền cả phần trăm lẫn số tiền, hoặc không điền gì* → `INVALID_VOUCHER_CONFIG` (4005).
- *Phần trăm ngoài 1–100* → `INVALID_DISCOUNT_PERCENT` (4006).
- *Số tiền ≤ 0* → `INVALID_DISCOUNT_AMOUNT` (4007).

---

### UC-V02 — Admin cập nhật voucher
**Actor:** Admin/Staff · **Tiền điều kiện:** có `UPDATE_VOUCHER`

**Luồng chính:**
1. Admin sửa thông tin voucher.
2. Phạm vi được **dựng lại toàn bộ** từ dữ liệu form gửi lên (không so khớp từng dòng).
3. `usedCount` **không** cho sửa thủ công — chỉ hệ thống tăng khi voucher vào đơn.

---

### UC-V03 — Admin xoá / bật-tắt hàng loạt
**Actor:** Admin/Staff

- **Xoá:** `DELETE_VOUCHER` — xoá **mềm** (`deleted_at`).
  Xoá hàng loạt theo danh sách id; nếu một id không tồn tại → lỗi `VOUCHER_NOT_FOUND`.
- **Bật/tắt:** `PATCH bulk-status` — đặt `active` cho cả lô.

---

### UC-V04 — Admin phát voucher đích danh
**Actor:** Admin/Staff · **Tiền điều kiện:** `UPDATE_VOUCHER`

**Luồng chính:**
1. Admin chọn voucher + danh sách khách (từ picker có tìm kiếm).
2. Hệ thống kiểm tra voucher còn dùng được.
3. Với mỗi khách: nếu **đã có** voucher này → **bỏ qua**; nếu id không tồn tại → bỏ qua.
4. Tạo `UserVoucher` trạng thái `AVAILABLE`; nguồn = `CLAIMED` nếu voucher PUBLIC,
   ngược lại `GIFTED`.
5. Trả về **số voucher thực sự phát thêm**.

**Vì sao bỏ qua thay vì báo lỗi?** Admin gán một danh sách dài thì vài khách trùng là
chuyện thường; chặn cả lô vì một người là sai. Lỗi ở bước phát **không** làm hỏng việc
điều hướng vì voucher đã được tạo trước đó.

---

### UC-V05 — Khách nhận voucher vào ví ("Lưu mã")
**Actor:** Customer đã đăng nhập

**Luồng chính:**
1. Khách mở trang **Ví voucher**, xem mục "Ưu đãi dành cho bạn".
2. Bấm "Lưu mã" trên một voucher.
3. Hệ thống kiểm tra: voucher là loại `PUBLIC`, còn dùng được, khách chưa nhận, còn lượt phát.
4. Tạo `UserVoucher` với `expiresAt` **chép từ** `voucher.expiryDate`.

**Luồng phụ:**
- *Voucher không phải PUBLIC* (ASSIGNED) → `USER_VOUCHER_NOT_CLAIMABLE` (4206).
- *Đã nhận rồi* → `USER_VOUCHER_ALREADY_CLAIMED` (4202).
- *Hết lượt phát* → `USER_VOUCHER_OUT_OF_STOCK` (4203).

> **BR-V07 — Hết lượt phát tính trên số ĐÃ PHÁT, không phải số đã dùng.** Voucher nằm
> trong ví chưa dùng vẫn chiếm suất. Ràng buộc thật nằm ở **unique index DB**
> `(user_id, voucher_id)` vì 2 request claim song song vẫn lọt qua bước kiểm tra ở service.

> **Vì sao chép hạn thay vì đọc `voucher.expiryDate`?** Admin sửa hạn của mẫu sau đó
> **không** được làm đổi hạn của voucher đã phát cho khách.

---

### UC-V06 — Khách xem ví voucher
**Actor:** Customer · **Endpoint:** `GET /client/vouchers?status=`

- `status` bỏ trống → cả ví. Lọc được theo `AVAILABLE` / `USED` / `EXPIRED`.
- Sắp xếp theo `acquiredAt` giảm dần (mới nhất trước).

---

### UC-V07 — Khách kiểm tra voucher ở giỏ hàng
**Actor:** Customer · **Endpoint:** `POST /client/vouchers/validate`

**Luồng chính:**
1. Khách chọn voucher từ ví ở overlay "Ưu đãi và khuyến mại".
2. Hệ thống tra voucher theo `userVoucherId` trong ví của chính khách.
3. Hệ thống **tự đọc giỏ của khách**, chạy engine promotion, tính `eligibleAmount`.
4. Kiểm tra lần lượt các điều kiện (xem BR-V04).
5. Trả về `{valid, code, discountAmount, forfeitedAmount, message}` — **luôn HTTP 200** kể cả
   khi voucher không hợp lệ, để FE hiển thị thông báo inline.

**Request:** `userVoucherId` (voucher trong ví khách). **Đã bỏ** đường gõ mã tay — voucher
chỉ vào đơn qua ví.

**Luồng phụ:**
- *Voucher không thuộc khách này / thiếu id* → `USER_VOUCHER_NOT_FOUND` (4201).
- *Voucher trong ví đã dùng* → `USER_VOUCHER_ALREADY_USED` (4205).
- *Voucher trong ví hết hạn* → `USER_VOUCHER_EXPIRED` (4204).

> **BR-V01 — BE tự tính, FE không gửi số tiền.** Request không có `orderTotal`. Nếu FE gửi
> số tiền lên thì (a) khách sửa được giá, (b) con số preview lệch với lúc chốt đơn vì thiếu
> kết quả promotion trên từng dòng.

> **BR-V13 — voucher từ ví phải đi qua API này.** Trước đây FE **tự tính** số tiền trên
> `subtotal` — bỏ qua **phạm vi** (scope) của voucher → lệch với số BE thu. Có ca lệch hàng
> chục triệu (xem BL-15 trong [05-backlog.md](05-backlog.md)).

---

### UC-V08 — Khách dùng voucher khi đặt hàng
**Actor:** Customer · **Endpoint:** `POST /client/orders`

**Luồng chính:**
1. Khách gửi kèm `userVoucherId` (voucher từ ví).
2. Hệ thống dựng đơn từ **giỏ server**, chạy promotion engine trước.
3. Tra voucher từ ví (kiểm tra thuộc đúng khách, chưa dùng, còn hạn).
4. Kiểm tra điều kiện (BR-V04), tính `eligibleAmount`, tính `voucherDiscount`.
5. Ghi `Order.voucherDiscount` + `Order.voucher` (snapshot).
6. Tăng `Voucher.usedCount`.
7. Đánh dấu `UserVoucher = USED`, gắn `order_id`, `usedAt`.

**Luồng phụ:**
- *Voucher từ ví của người khác* → `USER_VOUCHER_NOT_FOUND` (4201).
- *Voucher trong ví đã dùng* → `USER_VOUCHER_ALREADY_USED` (4205).
- *Mã sai ở bước chốt đơn* → ném `VOUCHER_NOT_USABLE` (5014), **không** âm thầm bỏ qua và
  thu nhiều tiền hơn khách tưởng.

---

### UC-V09 — Hoàn voucher khi huỷ đơn
**Actor:** Customer (tự huỷ) hoặc Admin (huỷ) hoặc Job (đơn VNPay quá hạn)

**Luồng chính:**
1. Đơn ở trạng thái `PENDING` hoặc `CONFIRMED` (state machine chặn hủy ở bước sau).
2. Hoàn tồn kho sản phẩm.
3. **Hoàn khuyến mại:**
   - `UserVoucher` (nếu có) → `AVAILABLE` (xoá `order_id`, `usedAt`) nếu **còn hạn**,
     ngược lại `EXPIRED`.
   - `Voucher.usedCount` −1.
   - `Promotion.usedCount` −1 cho từng chương trình đã áp (distinct theo `OrderDetail.promotionId`).
   - Kho phiên flash: hoàn `soldInFlash` cho dòng có `flashSaleItemId`.
4. Đặt trạng thái đơn `CANCELLED`.

> **BR-V09 — Tất cả trong CÙNG transaction** với việc đổi trạng thái đơn. Thiếu bước này
> thì khách **mất voucher oan** và ngân sách promotion/voucher bị trừ oan.
> Đơn `COMPLETED` không bao giờ hoàn — state machine đã chặn.

---

### UC-V10 — Job dọn voucher hết hạn
**Actor:** Hệ thống (`VoucherWalletService.expireOverdue()`)

- Quét `UserVoucher` có `status = AVAILABLE` và `expiresAt < now` → chuyển `EXPIRED`.
- Trả về số bản ghi đã đổi.
- ⏳ **Chưa gắn `@Scheduled`** — hiện chỉ có hàm, chưa có job chạy định kỳ.

---

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc | Nguồn |
|---|---|---|
| **BR-V01** | Preview ở giỏ và chốt đơn dùng **cùng một hàm** tính tiền. BE tự đọc giỏ, FE không gửi số tiền | `OrderService.validateVoucher` + `createOrder` dùng chung `checkVoucherRules` |
| **BR-V02** | Mức giảm chọn **đúng một** trong hai: `discountPercent` XOR `discountAmount` | `VoucherService.validateDiscountValue` |
| **BR-V03** | Mã voucher **unique** (không phân biệt hoa/thường), tự viết HOA + cắt khoảng trắng khi lưu | `VoucherService.validateCode` |
| **BR-V04** | Điều kiện áp voucher (kiểm theo thứ tự): còn `active` → chưa hết hạn → chưa hết lượt tổng → đã tới `startDate` → chưa chạm `perUserLimit` → `eligibleAmount > 0` → `eligibleAmount ≥ minOrderValue`. Mỗi nhánh vi phạm trả **một `ErrorCode` riêng** (xem §7.3) | `checkVoucherRules` + `resolveUnusableReason` |
| **BR-V05** | `eligibleAmount` = Σ(`lineTotal` − `lineDiscount`) của **các dòng khớp phạm vi**. Dùng cho **cả** `minOrderValue` **lẫn** base tính giảm | `VoucherService.calculateEligibleAmount` |
| **BR-V06** | Phạm vi khai `!= ALL` mà **danh sách giá trị rỗng** → **không khớp gì** (trả `false`) | `matchesScope` |
| **BR-V07** | `perUserLimit` đếm số lượt **ĐÃ DÙNG** của khách trên voucher này: chỉ tính bản ghi `user_vouchers` ở trạng thái `USED`. Claim (nhận) tạo bản ghi `AVAILABLE` — **chưa dùng thì không tính**. Hủy đơn hoàn voucher về `AVAILABLE` nên cũng không tính | `hasReachedPerUserLimit` |
| **BR-V08** | Voucher % có trần: `min(eligibleAmount × % / 100, maxDiscountAmount)`. Voucher số tiền không cần trần | `calculateDiscount` |
| **BR-V09** | Huỷ đơn hoàn **cả 3 thứ** (voucher về ví + `voucher.usedCount`−1 + `promotion.usedCount`−1) trong cùng transaction | `restorePromotions` |
| **BR-V10** | Chỉ voucher `PUBLIC` khách mới tự nhận được. `ASSIGNED` phải do admin phát | `claim` |
| **BR-V11** | Tối đa **1 voucher/đơn**, và voucher **chỉ vào đơn qua VÍ** (`userVoucherId`) — đã bỏ đường gõ mã tay | `createOrder` |
| **BR-V12** | `maxDiscountAmount` **phải chạy thật** — trước đây là field chết: lưu DB nhưng không được đọc, khiến voucher 10% trên đơn 90tr giảm thẳng 9tr, và FE preview áp trần còn BE thì không → lệch số | Đã sửa `calculateDiscount` |
| **BR-V13** | FE **không bao giờ tự tính tiền voucher**. Mọi con số giảm giá (kể cả khi chọn voucher từ ví) đều phải hỏi BE | `validateVoucher` + `/client/vouchers/validate` |
| **BR-V14** | **Voucher mệnh giá LỚN HƠN giá trị đơn → VẪN cho dùng**, giảm tối đa bằng phần tiền hàng còn lại (khách trả 0đ tiền hàng, vẫn trả phí ship). **Không chặn**, không báo lỗi. Voucher **bị tính là đã dùng** (tiêu 1 lượt) — chấp nhận mất phần chênh. **Có cảnh báo 2 lớp** (xem BR-V15) | `calculateDiscount` (kẹp ở `eligibleAmount`) + `createOrder` (kẹp ở `subtotal`) |
| **BR-V15** | **Cảnh báo mất tiền voucher** — khi `mệnh giá > eligibleAmount`, BE trả `forfeitedAmount = mệnh giá − số thực giảm` trong `/client/vouchers/validate`; FE hiện **2 lớp**: (1) khối amber inline trong overlay ưu đãi, (2) dialog xác nhận lúc bấm "Đặt hàng". Chỉ cảnh báo khi kẹp do **đơn nhỏ**; kẹp do trần `maxDiscountAmount` **không** cảnh báo (trần là thiết kế) | `calculateNominalDiscount` + `validateVoucher` |
| **BR-V16** | **Chi tiết đơn hiện rõ nguồn giảm** — khách xem lại đơn thấy *chương trình khuyến mại nào* (`promotionLines`: tên + tiền) và *voucher nào* (`voucherCode`), không chỉ tổng `discountAmount`. `promotionLines` gộp theo `promotionId` như màn admin (G10); chương trình đã xóa → `name = null` | `OrderService.toClientPromotionLines` + `OrderDetailResponse` |
| **BR-V17** | **Dòng tóm tắt tiền phải cộng lại ra tổng.** Đơn vị tiền hàng dùng để hiển thị là `totalBeforeDiscount` = Σ(giá × số lượng) — **KHÔNG** dùng `subtotal`, vì `subtotal` đã trừ giảm giá cấp dòng nên trừ tiếp dòng "Giảm giá sản phẩm" là **trừ hai lần**. Nhãn đổi "Tạm tính" → "Tiền hàng" (Tạm tính gợi ý "sẽ còn trừ nữa", gây hiểu sai ở màn xem lại đơn). Áp cho **cả** client lẫn admin | `OrderDetailResponse.totalBeforeDiscount` + `AdminOrderDetailResponse.totalBeforeDiscount` |
| **BR-V18** | **Dòng sản phẩm phải hiện ĐƠN GIÁ, không chỉ thành tiền.** `lineTotal` = `price × quantity − discountAmount` nên với đơn giá 100k giảm 10k, chỉ hiện thành tiền sẽ khiến khách tưởng đơn giá là 90k. Hiện `price × quantity` làm dòng phụ + dòng "Giảm giá: −X" khi có. Màn admin đã đúng (có cột đơn giá riêng) | `order-detail.component.html` (client) |

> **BR-V14 — quyết định 2026-09-29 (user chốt phương án A).** Đây là chủ ý, **không phải bug**:
> voucher là *phiếu giảm giá*, không phải *tiền hoàn* — không thể giảm quá giá trị đơn, nếu
> không `Cần thanh toán` sẽ **âm** (hệ thống phải trả tiền cho khách).
>
> **Ba lớp kẹp, theo thứ tự:**
> 1. `voucherDiscount = min(mệnh giá, eligibleAmount)` — không giảm quá phần tiền hàng
>    thuộc phạm vi, sau khi đã trừ promotion (D22).
> 2. `discountAmount = min(promotionDiscount + voucherDiscount, subtotal)` — tổng giảm không
>    vượt tiền hàng (D10).
> 3. `totalPrice = max(0, subtotal − discountAmount + shippingFee)` — sàn 0, cộng phí ship.
>
> **Hệ quả đã biết và đã chấp nhận:** khách dùng voucher 500k cho đơn 100k chỉ nhận được
> 90k giá trị, **mất 410k**, và voucher coi như đã dùng hết. **Có cảnh báo** (BR-V15, thêm 2026-09-30).
> **Không chọn phương án chặn (B)** vì voucher vẫn còn giá trị dùng được, chặn sẽ khiến khách
> không dùng được voucher — trong khi các sàn lớn (Shopee/Tiki) đều cho áp và coi như dùng hết.

> **BR-V15 — vì sao BE phải trả `forfeitedAmount` (thêm 2026-09-30).** BR-V13 cấm FE tự tính
> tiền voucher, mà FE chỉ có `discountAmount` của voucher trong ví, **không** có mệnh giá gốc
> → FE không thể tự biết đã bị kẹp. Nên BE phải nói ra. `calculateNominalDiscount`
> **cố ý không kẹp theo `orderTotal`** (khác `calculateDiscount`) — chính phần chênh đó là thứ cần đo.
>
> **Chỉ voucher SỐ TIỀN CỐ ĐỊNH mới mất.** Với kiểu phần trăm, mệnh giá
> `min(eligible × % / 100, maxDiscountAmount)` luôn ≤ `eligible` (vì % ≤ 100) nên `calculateDiscount`
> không bao giờ kẹp thêm → `forfeitedAmount = 0`. Vì vậy nhánh phần trăm gọi thẳng `calculateDiscount`.

> **BR-V16 — lưu ý `hasBreakdown` phải xét `!= null`, KHÔNG phải `!== undefined`.** BE trả
> `null` (không phải `undefined`) cho `promotionDiscount`/`voucherDiscount` của đơn cũ trước
> Sprint 1. Nếu chỉ so với `undefined` thì đơn cũ bị coi là "có breakdown", hai dòng đều bằng 0
> nên **không hiện gì** → mất luôn dòng "Giảm giá" của đơn cũ. Đây là bẫy dễ mắc khi nối thêm
> input optional vào `app-order-summary`.

> **BR-V06 — vì sao quan trọng:** voucher khai "chỉ áp danh mục Laptop" nhưng chưa chọn
> danh mục nào. Nếu trả `true` thì voucher biến thành "áp cả đơn" — sai hoàn toàn ý định
> của admin và thất thoát tiền.

---

## 5. Luồng nghiệp vụ

```
┌─ ADMIN ────────────────────────────────────────────────────────────────┐
│  Tạo mẫu voucher (code, mức giảm, phạm vi, điều kiện)                 │
│      │                                                                 │
│      ├── phát đích danh ──→ UserVoucher (GIFTED) ──┐                   │
│      └── để PUBLIC ───────→ kho "Ưu đãi dành cho bạn"                  │
└────────────────────────────────────────────────────│───────────────────┘
                                                     │
┌─ CUSTOMER ──────────────────────────────────────────▼───────────────────┐
│  Bấm "Lưu mã" (claim) → UserVoucher (CLAIMED, AVAILABLE)                │
│      │                                                                  │
│      ▼                                                                  │
│  Ví voucher (tab: Khả dụng / Đã dùng / Hết hạn)                          │
│      │                                                                  │
│      ▼                                                                  │
│  Checkout: chọn voucher từ ví                                      │
│      │                                                                  │
│      ├─ preview (validate) ──→ hiện số tiền giảm                        │
│      ▼                                                                  │
│  Đặt hàng → Voucher USED + Order.voucherDiscount                        │
│      │                                                                  │
│      ├─ huỷ đơn ──→ về AVAILABLE (còn hạn) / EXPIRED                    │
│      └─ quá hạn ──→ EXPIRED (job)                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Dictionary

### 6.1 Bảng `vouchers` — mẫu voucher (entity `Voucher`)

| Cột | Kiểu | Null | Ý nghĩa | Ghi chú |
|---|---|:--:|---|---|
| `id` | varchar(255) | ✗ | Khoá chính (UUID) | |
| `code` | varchar(255) | ✗ | Mã voucher, VD `GIAM10` | **UNIQUE**, lưu dạng HOA |
| `title` | varchar(255) | ✓ | Tiêu đề hiển thị trên overlay giỏ | V10 |
| `description` | varchar(500) | ✓ | Mô tả / điều kiện hiển thị cho khách | V10 |
| `discount_percent` | int | ✓ | % giảm (1–100) | XOR với `discount_amount` |
| `discount_amount` | bigint | ✓ | Số tiền giảm, trừ **1 lần/đơn** | XOR với `discount_percent` |
| `start_date` | datetime(6) | ✓ | Bắt đầu được dùng; null = dùng ngay | |
| `expiry_date` | datetime(6) | ✓ | Hết hạn; null = không hạn | |
| `usage_limit` | int | ✓ | Lượt dùng tối đa toàn hệ thống | Mặc định 100 |
| `used_count` | int | ✓ | Số lượt đã dùng | Mặc định 0, chỉ hệ thống tăng |
| `active` | bit(1) | ✗ | Còn dùng được | Mặc định 1 |
| `min_order_value` | bigint | ✓ | Đơn tối thiểu (xét trên `eligibleAmount`) | null = không yêu cầu |
| `max_discount_amount` | bigint | ✓ | Trần giảm (cho voucher %) | null = không trần |
| `per_user_limit` | int | ✓ | Số lượt tối đa mỗi khách | null = không giới hạn |
| `scope_type` | enum | ✓ | `ALL`/`CATEGORY`/`BRAND`/`PRODUCT` | null = `ALL` |
| `scope_value` | varchar(255) | ✓ | **@Deprecated** — phạm vi 1 giá trị thời kỳ đầu | Giữ để `ddl-auto=validate` không lệch |
| `voucher_type` | enum(`ASSIGNED`,`PUBLIC`) | ✓ | Kiểu phát hành; null = `PUBLIC` |
| `created_at` / `updated_at` | datetime(6) | ✓ | Audit tự động | |
| `deleted_at` | datetime(6) | ✓ | Xoá mềm | `@SQLRestriction` lọc tự động |

### 6.2 Bảng `voucher_scopes` — phạm vi (entity `VoucherScope`)

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | varchar(255) | Khoá chính |
| `voucher_id` | varchar(255) | FK → `vouchers.id` |
| `target_type` | enum(`ALL`,`BRAND`,`CATEGORY`,`PRODUCT`) | Loại phạm vi |
| `target_value` | varchar(255) | `Category.id` \| `Product.factory` \| `Product.id` |

> Một voucher có **nhiều** dòng scope (giảm cho cả danh mục Laptop lẫn Phụ kiện).
> Không có dòng nào = áp toàn đơn. `BRAND` chuẩn hoá **trim + UPPERCASE** khi lưu vì so
> khớp với `Product.factory`.

### 6.3 Bảng `user_vouchers` — ví voucher (entity `UserVoucher`)

| Cột | Kiểu | Null | Ý nghĩa |
|---|---|:--:|---|
| `id` | varchar(255) | ✗ | Khoá chính |
| `user_id` | varchar(255) | ✗ | FK → `users.id` |
| `voucher_id` | varchar(255) | ✗ | FK → `vouchers.id` (mẫu) |
| `status` | enum(`AVAILABLE`,`EXPIRED`,`USED`) | ✗ | Trạng thái trong ví |
| `source` | enum(`BIRTHDAY`,`CLAIMED`,`GIFTED`,`WELCOME`) | ✗ | Đường vào ví |
| `acquired_at` | datetime(6) | ✗ | Thời điểm nhận |
| `expires_at` | datetime(6) | ✓ | Hạn dùng, **chép từ mẫu lúc nhận**; null = trường tồn |
| `used_at` | datetime(6) | ✓ | Thời điểm dùng |
| `order_id` | varchar(255) | ✓ | FK → `orders.id`; null khi chưa dùng |

> **UNIQUE (`user_id`, `voucher_id`)** chống claim trùng — ràng buộc ở DB chứ không chỉ ở
> service, vì 2 request claim song song vẫn lọt qua bước kiểm tra.

### 6.4 Enum

| Enum | Giá trị | Ý nghĩa |
|---|---|---|
| `VoucherType` | `PUBLIC` | Khách tự claim vào ví, không cần admin phát |
| | `ASSIGNED` | Admin gán tay cho 1 khách (phải có trong ví mới dùng được) |
| `ScopeType` | `ALL` / `CATEGORY` / `BRAND` / `PRODUCT` | Phạm vi áp dụng |
| `UserVoucherStatus` | `AVAILABLE` / `USED` / `EXPIRED` | Trạng thái trong ví |
| `UserVoucherSource` | `CLAIMED` / `GIFTED` / `WELCOME` / `BIRTHDAY` | Đường vào ví (khác `VoucherType` — đây là **nguồn**, không phải **kiểu phát hành**) |

---

## 7. API Contract

### 7.1 Admin — `/api/v1/admin/vouchers`

| Method | Path | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/vouchers` | `READ_VOUCHER` | — | `List<VoucherResponse>` |
| GET | `/vouchers/{id}` | `READ_VOUCHER` | — | `VoucherResponse` |
| POST | `/vouchers` | `CREATE_VOUCHER` | `@ModelAttribute VoucherCreationRequest` (form-data) | `VoucherResponse` |
| PUT | `/vouchers/{id}` | `UPDATE_VOUCHER` | `@ModelAttribute VoucherUpdateRequest` | `VoucherResponse` |
| DELETE | `/vouchers/{id}` | `DELETE_VOUCHER` | — | — |
| POST | `/vouchers/bulk-delete` | `DELETE_VOUCHER` | `{ ids: [...] }` | — |
| PATCH | `/vouchers/bulk-status` | `UPDATE_VOUCHER` | `{ ids: [...], active: bool }` | — |
| POST | `/vouchers/assign` | `UPDATE_VOUCHER` | `{ voucherId, userIds: [...] }` | `int` (số đã phát) |
| GET | `/vouchers/{id}/holders` | `READ_VOUCHER` | — | `List<VoucherHolderResponse>` |

**Vì sao create/update dùng `@ModelAttribute` (form-data)?** Form có nhiều trường, giữ
nguyên khuôn `@ModelAttribute` cho đồng bộ với Category/Product. **Đã bỏ ảnh** — không còn
`inputFile`/`imageUrl`/`removeImage`.

**`VoucherCreationRequest`:**

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:--:|---|
| `code` | String | ✓ | |
| `title` | String | | |
| `description` | String | | |
| `discountPercent` | Integer | | XOR với `discountAmount` |
| `discountAmount` | Long | | XOR với `discountPercent` |
| `startDate` | LocalDateTime | | ISO date-time |
| `expiryDate` | LocalDateTime | | |
| `usageLimit` | Integer | | |
| `minOrderValue` | Long | | |
| `maxDiscountAmount` | Long | | |
| `perUserLimit` | Integer | | |
| `scopeType` | ScopeType | | |
| `scopeValues` | List\<String\> | | |
| `voucherType` | VoucherType | | |
| `active` | boolean | | Mặc định true |

`VoucherUpdateRequest` = như trên (không có trường riêng).

**`VoucherResponse`:** `id, code, title, description, discountPercent, discountAmount,
startDate, expiryDate, usageLimit, usedCount, active, minOrderValue, maxDiscountAmount,
perUserLimit, scopeType, scopeValues, voucherType, createdAt, updatedAt`.

**`VoucherHolderResponse`** (bảng "Khách đã nhận"): `id, userId, userName, userEmail,
source, status, acquiredAt, expiresAt, usedAt`.

---

### 7.2 Client — `/api/v1/client/vouchers`

| Method | Path | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/vouchers?status=` | `isAuthenticated()` | — | `List<UserVoucherResponse>` (ví) |
| GET | `/vouchers/available` | `isAuthenticated()` | — | `List<VoucherResponse>` (kho nhận được) |
| POST | `/vouchers/claim/{voucherId}` | `isAuthenticated()` | — | `UserVoucherResponse` |
| POST | `/vouchers/validate` | `isAuthenticated()` | `{ userVoucherId }` | `VoucherValidationResponse` |

**`VoucherValidationResponse`:** `{ valid: bool, code, discountAmount: Long, forfeitedAmount: Long, message: String }`.
Luôn HTTP 200 — kể cả voucher không hợp lệ — để FE hiện thông báo inline.
`forfeitedAmount` = phần mệnh giá voucher không dùng được vì đơn nhỏ hơn mệnh giá (BR-V15);
`> 0` thì FE hiện cảnh báo mất tiền. Không tính phần bị cắt bởi trần `maxDiscountAmount`.

**`UserVoucherResponse`:** `id, voucherId, code, discountPercent, discountAmount,
minOrderValue, maxDiscountAmount, status, source, acquiredAt, expiresAt, usedAt`.

---

### 7.3 Bảng mã lỗi

| Code | Hằng số | HTTP | Thông báo | Dùng ở |
|---|---|---|---|---|
| 4001 | `VOUCHER_NOT_FOUND` | 404 | Không tìm thấy voucher | CRUD admin · validate · chốt đơn |
| 4002 | `VOUCHER_ALREADY_EXISTS` | 400 | Voucher này đã tồn tại trong hệ thống | Tạo/sửa voucher |
| 4003 | `VOUCHER_EXPIRED` | 400 | Voucher đã hết hạn sử dụng | Nhánh chặn #1b |
| 4004 | `VOUCHER_OUT_OF_STOCK` | 400 | Voucher đã hết lượt sử dụng | Nhánh chặn #1c |
| 4005 | `INVALID_VOUCHER_CONFIG` | 400 | Cấu hình giảm giá không hợp lệ (chỉ chọn Phần trăm hoặc Số tiền) | Tạo/sửa voucher |
| 4006 | `INVALID_DISCOUNT_PERCENT` | 400 | Phần trăm giảm giá phải nằm trong khoảng 1–100 | Tạo/sửa voucher |
| 4007 | `INVALID_DISCOUNT_AMOUNT` | 400 | Số tiền giảm giá phải lớn hơn 0 | Tạo/sửa voucher |
| 4008 | `VOUCHER_CODE_REQUIRED` | 400 | Voucher không được để trống | `@NotBlank` DTO |
| 4009 | `INVALID_VOUCHER_DATA` | 400 | Dữ liệu voucher không hợp lệ | `@NotEmpty` bulk request |
| 4010 | `VOUCHER_SCOPE_INVALID` | 400 | Phạm vi áp dụng của voucher không hợp lệ | *chưa dùng* |
| 4011 | `VOUCHER_MIN_ORDER_NOT_MET` | 400 | Đơn hàng chưa đạt giá trị tối thiểu | Nhánh chặn #5 |
| 4012 | `VOUCHER_PER_USER_LIMIT_REACHED` | 400 | Bạn đã dùng hết số lượt cho phép | Nhánh chặn #3 |
| 4013 | `VOUCHER_NOT_STARTED` | 400 | Voucher chưa đến thời gian sử dụng | Nhánh chặn #2 |
| 4014 | `VOUCHER_NOT_IN_WALLET` | 403 | Voucher này không có trong ví của bạn | *chưa dùng* |
| **4015** | **`VOUCHER_INACTIVE`** | 400 | Voucher đã bị khoá hoặc ngừng áp dụng | **Nhánh chặn #1a** *(mới 2026-09-28)* |
| **4017** | **`VOUCHER_NO_ELIGIBLE_ITEM`** | 400 | Voucher không áp dụng cho sản phẩm nào trong đơn | **Nhánh chặn #4** *(mới 2026-09-28)* |
| **4018** | **`VOUCHER_NO_DISCOUNT`** | 400 | Voucher không tạo ra khoản giảm nào cho đơn này | **Giảm ra ≤ 0** *(mới 2026-09-28)* |
| 4201 | `USER_VOUCHER_NOT_FOUND` | 404 | Không tìm thấy voucher trong ví | Ví — không thuộc khách |
| 4202 | `USER_VOUCHER_ALREADY_CLAIMED` | 400 | Bạn đã nhận voucher này rồi | Claim |
| 4203 | `USER_VOUCHER_OUT_OF_STOCK` | 400 | Voucher đã hết lượt nhận | Claim |
| 4204 | `USER_VOUCHER_EXPIRED` | 400 | Voucher đã hết hạn | Ví — quá hạn |
| 4205 | `USER_VOUCHER_ALREADY_USED` | 400 | Voucher này đã được sử dụng | Ví — đã dùng |
| 4206 | `USER_VOUCHER_NOT_CLAIMABLE` | 400 | Voucher này không thể nhận trước | Claim (ASSIGNED) |
| 5014 | `VOUCHER_NOT_USABLE` | 400 | Voucher không hợp lệ hoặc đã hết hạn | *fallback* — xem ghi chú |

> **Cập nhật 2026-09-28 — ErrorCode chi tiết cho từng nhánh.** Trước đây `checkVoucherRules`
> trả **chuỗi message** và ở bước **chốt đơn** chuỗi đó **bị vứt đi**, chỉ ném
> `VOUCHER_NOT_USABLE` chung chung → khách thấy lý do khác với preview. Nay hàm trả
> **`ErrorCode`**, và **cả preview lẫn chốt đơn dùng chung một mã** → không bao giờ lệch lý do.
>
> Nhánh `isVoucherUsable()` (gộp 3 điều kiện) được tách bằng `resolveUnusableReason()`
> thành 3 mã riêng: bị khoá (4015) / hết hạn (4003) / hết lượt (4004) — nhờ vậy 4003 và
> 4004 từ **mã chết** trở thành **mã dùng thật**.
>
> `VOUCHER_NOT_USABLE` (5014) nay chỉ còn là **fallback** cho trường hợp không xác định
> được lý do cụ thể; luồng chính đã dùng mã chi tiết.

---

## 8. Màn hình & tương tác

### 8.1 Admin

| Màn | Nội dung |
|---|---|
| **Danh sách Voucher** (`/admin/vouchers`) | KPI + filter + bulk toolbar + bảng 8 cột (kèm menu kebab 3 chấm) + phân trang |
| **Form Voucher** (`/admin/vouchers/create`, `/:id/edit`) | Mã, tiêu đề, mô tả, mức giảm (tab % / số tiền), thời gian, lượt dùng, **scope-picker** (tab loại + hộp tick + chip), điều kiện (đơn tối thiểu, trần giảm, lượt/khách), trạng thái, panel **preview** cách tính |
| **Chi tiết Voucher** (`/admin/vouchers/:id`) | KPI 4 thẻ, thông tin, phạm vi, timeline, **bảng "Khách đã nhận"**, card "Phát hành & phạm vi" |

**Thành phần dùng chung:** `scope-picker` (chọn nhiều giá trị theo tab loại), `user-picker`
(phát voucher cho khách, có tìm kiếm phân trang), `voucher-card` (card vé ở client).

> **Quy ước UI:** form admin bám mockup `design-mockup/promotion-admin-preview.html` —
> dùng **tab + tick + chip** cho phạm vi, **không** dùng `<select>`. Không để lộ thuật ngữ
> kỹ thuật (`eligibleAmount`, mã `D21`) ra UI.

### 8.2 Client

| Màn | Nội dung |
|---|---|
| **Ví voucher** (`/vouchers`) | Tab **Khả dụng / Đã dùng / Hết hạn**; mục "Ưu đãi dành cho bạn" (kho claim) với nút "Lưu mã". Card dùng chung `app-voucher-card`, hiện **giới hạn mỗi khách** (`perUserLimit`) + **tổng lượt** (`usageLimit`) — trường trống hiện "Không giới hạn" |
| **Overlay ưu đãi** (trong cart + checkout) | Danh sách voucher từ ví (`app-voucher-card` chế độ chọn, có dòng giới hạn lượt/khách + tổng lượt); chọn/bỏ chọn → tiền cập nhật ngay. Khi voucher bị kẹp → **khối amber** cảnh báo mất tiền (BR-V15) |
| **Sidebar đơn hàng** (`app-order-summary`) | Tạm tính / Tổng khuyến mại (Giảm giá sản phẩm + Voucher) / Phí vận chuyển / Cần thanh toán. Khi voucher bị kẹp → dòng cảnh báo nhỏ dưới dòng "Voucher" (BR-V15). Dòng "Voucher" kèm **mã** (`voucherCode`), dưới "Giảm giá sản phẩm" liệt kê **tên từng chương trình** (`promotionLines`) |
| **Chi tiết đơn hàng** (`/orders/:id`) | `app-order-summary` nhận đủ breakdown từ `OrderDetailResponse`: `promotionDiscount` + `voucherDiscount` + `voucherCode` + `promotionLines` → khách xem lại đơn thấy rõ **khuyến mãi nào, voucher nào** (BR-V16). Đơn cũ (`promotionDiscount`/`voucherDiscount` = **null**) rơi về nhánh "Giảm giá" gộp — xem lưu ý `hasBreakdown` bên dưới |
| **Checkout — nút "Đặt hàng"** | Voucher bị kẹp → **dialog xác nhận** "Voucher vượt giá trị đơn" trước khi tạo đơn (BR-V15). `window.open` cho VNPay phải mở TRƯỚC dialog (nếu sau sẽ bị trình duyệt chặn popup) |

---

## 9. Edge case

| # | Tình huống | Xử lý |
|---|---|---|
| E-V01 | Voucher khai `scopeType != ALL` nhưng `scopes` rỗng | Trả `false` — **không khớp gì** (BR-V06). Nếu trả `true` sẽ biến voucher "chỉ danh mục X" thành "áp cả đơn" |
| E-V02 | Giỏ 40tr nhưng chỉ 10tr thuộc phạm vi, voucher yêu cầu đơn từ 20tr | **Chặn** — `minOrderValue` xét trên `eligibleAmount` (10tr), không phải 40tr |
| E-V03 | Voucher 10% trên đơn 90tr, `maxDiscountAmount = 500k` | Giảm **500k**, không phải 9tr |
| E-V04 | Khách claim voucher (`perUserLimit = 1`) rồi mới dùng lần đầu | **Cho dùng** — claim tạo bản ghi `AVAILABLE`, chưa tính là một lượt. Dùng xong (bản ghi `USED`) thì chạm trần, lần sau bị chặn (BR-V07) |
| E-V05 | Hai request claim cùng voucher song song | **Unique index DB** chặn — bước kiểm tra ở service có thể lọt |
| E-V06 | Admin sửa `expiryDate` của mẫu sau khi đã phát voucher | Hạn của voucher **đã phát** không đổi (`expiresAt` đã chép) |
| E-V07 | Huỷ đơn dùng voucher → voucher hết hạn trong lúc đó | Về `EXPIRED`, không về `AVAILABLE` |
| E-V08 | Huỷ đơn `COMPLETED` | **Không cho huỷ** (state machine) → không hoàn |
| E-V09 | Đơn cũ (trước module) có `promotion_discount`/`voucher_discount` = NULL | Getter null-safe trả `0L`; migration V2 đã backfill `voucher_discount = discount_amount` |
| E-V10 | Phát voucher cho danh sách có khách trùng / id lạ | **Bỏ qua**, không chặn cả lô; trả số thực phát |
| E-V11 | Preview hiện 1 số, đơn tạo ra số khác | **Không xảy ra** — cùng hàm `checkVoucherRules` + cùng engine (BR-V01) |
| E-V12 | Tổng giảm vượt `subtotal` | Cap `discountAmount = min(promotion + voucher, subtotal)` → `Cần thanh toán` không bao giờ âm |
| E-V13 | Khách chọn voucher từ ví mà voucher có **phạm vi** (VD chỉ áp hãng Dell) nhưng giỏ toàn ASUS | BE trả `valid = false`, `discountAmount = 0` (BR-V13). FE **không** được tự tính — nếu tự tính trên `subtotal` sẽ báo giảm 50% cả đơn (lệch tới hàng chục triệu) |
| E-V14 | Chọn voucher từ ví nhưng request còn sót trường mã gõ tay | Không còn xảy ra — đường gõ mã tay đã bỏ hẳn khỏi DTO/BE |
| E-V15 | **Voucher 500k cho đơn 100k** (mệnh giá > giá trị đơn) | **Cho dùng** (BR-V14): `voucherDiscount` kẹp ở `eligibleAmount`, tổng giảm kẹp ở `subtotal`. Khách trả **0đ tiền hàng** + phí ship. Voucher bị tính đã dùng. `forfeitedAmount = 410.000` → FE cảnh báo 2 lớp (BR-V15) |
| E-V16 | Đơn 100k có promotion 10k + voucher "giảm thẳng 500k" | `eligibleAmount` = 100.000 − 10.000 = **90.000**; `voucherDiscount` = min(500.000, 90.000) = 90.000; `promotionDiscount` = 10.000; `discountAmount` = min(90.000 + 10.000, 100.000) = **100.000**; `totalPrice` = 100.000 − 100.000 + 50.000 ship = **50.000**. Tiền hàng về 0, còn lại chỉ là **phí vận chuyển** (đơn < 2 triệu nên không freeship). `forfeitedAmount = 410.000` |
| E-V17 | Voucher **phần trăm** trên đơn nhỏ | VD 100% trên đơn 100k → giảm 100k, cùng kết quả như E-V16. `forfeitedAmount = 0` — **không cảnh báo** (mệnh giá % không bao giờ vượt đơn) |
| E-V18 | Voucher **phần trăm có trần**, đơn lớn | VD 10% trần 2tr trên đơn 30tr → giảm 2tr do chạm trần. `forfeitedAmount = 0` — **không cảnh báo**: trần là thiết kế chương trình, không phải tiền khách bị mất |

---

## 10. Acceptance Criteria

| Mã | Tiêu chí |
|---|---|
| **AC-V01** | Tạo voucher mã trùng (khác hoa/thường) → lỗi `VOUCHER_ALREADY_EXISTS` |
| **AC-V02** | Điền cả `discountPercent` lẫn `discountAmount` → lỗi `INVALID_VOUCHER_CONFIG` |
| **AC-V03** | `discountPercent = 150` → lỗi `INVALID_DISCOUNT_PERCENT` |
| **AC-V04** | Claim voucher PUBLIC → thấy trong ví; claim lần 2 → lỗi `USER_VOUCHER_ALREADY_CLAIMED` |
| **AC-V05** | Claim voucher `ASSIGNED` → lỗi `USER_VOUCHER_NOT_CLAIMABLE` |
| **AC-V06** | Dùng voucher từ ví → `status = USED`, `used_at` + `order_id` được ghi; dùng lại → `USER_VOUCHER_ALREADY_USED` |
| **AC-V07** | Voucher **chỉ vào đơn qua ví** (`userVoucherId`); DTO/BE không còn trường mã gõ tay |
| **AC-V08** | Voucher "Laptop văn phòng, đơn từ 20tr", giỏ 40tr nhưng chỉ 10tr thuộc scope → **chặn**; giỏ 25tr thuộc scope → **được**, giảm tính trên 25tr (đã trừ promotion), không phải 40tr |
| **AC-V09** | Voucher `scopeType = ALL` → số tiền khớp **hành vi cũ** (không hồi quy) |
| **AC-V10** | Voucher 10% + `maxDiscountAmount = 500k` trên đơn 90tr → giảm đúng **500k** |
| **AC-V11** | `perUserLimit = 1`: claim voucher (chưa dùng) → **áp được**; sau khi dùng xong ở đơn 1 → lần sau **chặn** |
| **AC-V12** | Huỷ đơn dùng voucher → voucher về `AVAILABLE` (nếu còn hạn), `usedCount` voucher −1, `usedCount` promotion −1 |
| **AC-V13** | Số tiền preview ở overlay **khớp 100%** số tiền ghi vào đơn sau khi đặt (chọn từ ví) |
| **AC-V13b** | Chọn voucher từ ví có phạm vi không khớp giỏ → FE hiện `valid = false` (không tự tính giảm) |
| **AC-V13c** | Chọn voucher từ ví → FE **có gọi** `POST /client/vouchers/validate` với `userVoucherId` (kiểm qua network tab) |
| **AC-V14** | Sửa request `CreateOrderRequest` (đổi số tiền) → **không** làm sai số tiền đơn (BE luôn tính lại) |
| **AC-V15** | `Cần thanh toán` không bao giờ < 0 |
| **AC-V15b** | Voucher mệnh giá > giá trị đơn (VD 500k cho đơn 100k) → **đơn tạo thành công**, `discountAmount = subtotal`, `totalPrice` = `shippingFee`. **Không** ném lỗi (BR-V14) |
| **AC-V15c** | Đơn 100k + promotion 10k + voucher 500k → `promotionDiscount = 10.000`, `voucherDiscount = 90.000`, `discountAmount = 100.000`, `totalPrice = 50.000` (chỉ còn phí ship) |
| **AC-V15d** | Voucher 500k cho đơn 90k → `validate` trả `forfeitedAmount = 410.000`; overlay hiện khối amber và dialog xác nhận hiện khi bấm "Đặt hàng" (BR-V15) |
| **AC-V15e** | Voucher **phần trăm** (kể cả có trần) → `forfeitedAmount = 0`, **không** hiện cảnh báo nào |
| **AC-V16b** | Đơn có khuyến mại + voucher → chi tiết đơn hiện tên **từng chương trình** (dưới "Giảm giá sản phẩm") và **mã voucher** (cạnh dòng "Voucher"); `promotionLines` gộp đúng theo `promotionId` |
| **AC-V16c** | Đơn **cũ** (trước Sprint 1, `promotionDiscount`/`voucherDiscount` = null) → vẫn hiện **1 dòng "Giảm giá"** gộp, không hiện dòng trống |
| **AC-V17b** | Mọi dòng tóm tắt tiền ở chi tiết đơn **cộng lại đúng ra tổng**: `totalBeforeDiscount − promotionDiscount − voucherDiscount + shippingFee = totalPrice`. Kiểm trên đơn có cả khuyến mại lẫn voucher (E-V16) |
| **AC-V17c** | `totalBeforeDiscount` = Σ(giá × số lượng), **lớn hơn** `subtotal` khi đơn có giảm giá cấp dòng. Đơn không khuyến mại thì hai số **bằng nhau** |
| **AC-V18b** | Dòng sản phẩm ở chi tiết đơn hiện `đơn giá × số lượng` + dòng "Giảm giá: −X" (khi có), **không** chỉ hiện thành tiền — tránh nhầm đơn giá đã giảm thành đơn giá gốc |
| **AC-V16** | Đơn cũ (trước module) vẫn xem được ở cả client lẫn admin, số tiền không đổi |
| **AC-V17** | STAFF thấy menu Voucher, tạo/sửa được, **không** có nút Xoá. CUSTOMER gọi API admin → 403 |
| **AC-V18** | Phát voucher cho 5 khách trong đó 2 đã có → trả về **3**, không lỗi |

---

## 11. Truy vết

| BR | UC | AC | Code |
|---|---|---|---|
| BR-V01 | UC-V07, UC-V08 | AC-V13, AC-V14 | `OrderService.validateVoucher` / `createOrder` / `checkVoucherRules` |
| BR-V02 | UC-V01 | AC-V02, AC-V03 | `VoucherService.validateDiscountValue` |
| BR-V03 | UC-V01 | AC-V01 | `VoucherService.validateCode` |
| BR-V04 | UC-V07, UC-V08 | AC-V08, AC-V11 | `OrderService.checkVoucherRules` |
| BR-V05 | UC-V07, UC-V08 | AC-V08 | `VoucherService.calculateEligibleAmount` |
| BR-V06 | UC-V07 | AC-V08 | `VoucherService.matchesScope` |
| BR-V07 | UC-V08 | AC-V11 | `VoucherService.hasReachedPerUserLimit` |
| BR-V08 | UC-V08 | AC-V10 | `VoucherService.calculateDiscount` |
| BR-V09 | UC-V09 | AC-V12 | `OrderService.restorePromotions` |
| BR-V10 | UC-V05 | AC-V04, AC-V05 | `VoucherWalletService.claim` |
| BR-V11 | UC-V08 | AC-V07 | `OrderService.createOrder` |
| BR-V12 | UC-V08 | AC-V10 | `VoucherService.calculateDiscount` |
| BR-V13 | UC-V07, UC-V08 | AC-V13, AC-V13b, AC-V13c | `OrderService.validateVoucher` + `ValidateVoucherRequest.userVoucherId` |
| BR-V14 | UC-V08 | AC-V15, AC-V15b, AC-V15c | `VoucherService.calculateDiscount` + `OrderService.createOrder` (2 lớp kẹp) |
