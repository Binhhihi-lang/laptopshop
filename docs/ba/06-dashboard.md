# 06 — DASHBOARD (Bảng điều khiển quản trị)

> Tài liệu nghiệp vụ · v1.0 · 2026-10-03
> Đọc kèm: [README.md](README.md) · [02-promotion.md](02-promotion.md) · [03-flash-sale.md](03-flash-sale.md)
> Mockup đã duyệt: `design-mockup/admin-dashboard-preview.html`

---

## 1. Mục tiêu & phạm vi

### 1.1 Mục tiêu

Biến **Bảng điều khiển** từ màn hình "trang trí" thành **bảng điều khiển thật**: mọi con
số đều lấy từ dữ liệu sống, có biểu đồ doanh thu theo thời gian, và **phân biệt rõ góc nhìn
Admin vs Nhân viên**.

### 1.2 Vấn đề của bản hiện tại

| # | Vấn đề | Bằng chứng |
|---|---|---|
| 1 | **3 thẻ KPI là số 0 cứng** | `revenueToday = 0`, `recentOrdersCount = 0` trong `dashboard.component.ts` |
| 2 | **Khối "Hoạt động gần đây" là dữ liệu MẪU** | `buildSampleActivity()` hardcode "Đơn #ORD-2024-001", "SUMMER20"… |
| 3 | **Biểu đồ doanh thu chỉ là placeholder** | Chuỗi "Tích hợp biểu đồ sắp ra mắt", canvas rỗng |
| 4 | **Trend pill vô nghĩa** | "Danh mục — N đang hoạt động" nhưng N là **tổng**, không phải số đang hoạt động |
| 5 | **Bộ lọc thời gian chết** | Select "Tuần này" + 2 nút ‹ › không gắn logic |
| 6 | **Không phân biệt vai trò** | STAFF thấy y hệt ADMIN |

### 1.3 Trong phạm vi

- Mở rộng endpoint `GET /api/v1/admin/dashboard/stats` trả **số liệu tổng hợp thật**:
  doanh thu theo kỳ + so kỳ trước, đếm đơn theo trạng thái, chuỗi doanh thu theo ngày,
  top sản phẩm bán chạy.
- Bố cục mới: 8 thẻ KPI chia 2 hàng + thanh lọc khoảng thời gian + 2 biểu đồ + 2 danh sách.
- Thay placeholder bằng **ApexCharts** (area chart doanh thu + donut trạng thái đơn).
- **Gating theo quyền**: ẩn/hiện từng khối theo `READ_USER` / vai trò ADMIN.
- **Bỏ hẳn** khối "Hoạt động gần đây".

### 1.4 Ngoài phạm vi (đã chốt, KHÔNG làm)

- **Nhật ký hoạt động / audit log** — không có bảng log trong hệ thống; dựng audit log là
  hạng mục riêng (gắn với chức năng **Thông báo**, làm sau).
- **Báo cáo hiệu quả BANNER** — hệ thống **không theo dõi** lượt xem/lượt bấm banner (không
  có bảng/cột nào). Muốn có phải xây tracking mới (thêm cột/bảng + endpoint ghi nhận + FE
  gọi khi hiển thị/bấm) — hạng mục cỡ vừa, **để riêng**.
- **Doanh thu theo danh mục / thương hiệu** — chỉ làm theo **sản phẩm** (top 5).
- **Lợi nhuận / chi phí** — hệ thống **không lưu giá vốn**, nên không thể tính lợi nhuận.
  Mockup cũ có legend "Lợi nhuận / Chi phí" → **bỏ**.
- **Báo cáo dạng danh sách chi tiết đơn** (đối soát kế toán) — file xuất là **tổng hợp
  theo kỳ**, không liệt kê từng đơn.

> **Nguyên tắc:** Dashboard là màn **CHỈ ĐỌC** — không tạo/sửa/xoá dữ liệu nào. Mọi con số
> là kết quả tổng hợp từ bảng đã có.

### 1.5 Bổ sung 2026-10-03 (đợt 2)

- **Xuất báo cáo Excel** (`GET /dashboard/export`) — file `.xlsx` 4 sheet, quyền `READ_USER`
  (chỉ ADMIN). Xem §8.3.
- **Khối "Hiệu quả khuyến mại"** trên Dashboard — chi phí promotion/voucher/flash + top
  chương trình. Xem §9.4 và BR-D11…BR-D14.

---

## 2. Actor

| Actor | Mô tả | Quyền dashboard |
|---|---|---|
| **Admin** | Quản trị viên | Xem **toàn bộ** khối, kể cả Khách hàng + Xuất báo cáo |
| **Staff** | Nhân viên bán hàng / giữ kho | Xem khối **vận hành**; **KHÔNG** xem Khách hàng, **KHÔNG** Xuất báo cáo |
| **Customer** | Khách | Không truy cập được `/admin/dashboard` |

> Cả Admin và Staff đều có quyền `READ_DASHBOARD` (xem §7). Việc tách khối **không** dựa
> trên `READ_DASHBOARD` mà dựa trên các quyền **chi tiết hơn** (`READ_USER`) và **vai trò**.

---

## 3. Use Case

### UC-D01 — Xem bảng điều khiển
**Actor:** Admin/Staff · **Tiền điều kiện:** `READ_DASHBOARD`

**Luồng chính:**
1. Người dùng mở `/admin/dashboard`.
2. FE gọi **một** endpoint `GET /api/v1/admin/dashboard/stats?range=LAST_30_DAYS`.
3. BE tổng hợp số liệu trong kỳ và kỳ liền trước, trả về một DTO duy nhất.
4. FE dựng: 8 thẻ KPI → thanh lọc kỳ → biểu đồ doanh thu + donut trạng thái → top bán chạy
   + danh sách sắp hết hàng.
5. FE **ẩn** các khối mà người dùng không có quyền (xem §7).

**Luồng phụ:**
- *Thiếu `READ_DASHBOARD`* → BE trả **403**; FE hiện empty-state "quyền bị thu hồi".
- *Lỗi mạng / 5xx* → FE hiện empty-state lỗi + nút "Thử lại"; **không** hiện stack trace.
- *DB trống (chưa có đơn nào)* → mọi con số = 0, biểu đồ hiện trục rỗng + thông điệp
  "Chưa có dữ liệu", **không vỡ layout**.

> **BR-D01 — MỘT endpoint duy nhất.** Dashboard **không** gọi `forkJoin` nhiều API.
> Lý do: STAFF thiếu `READ_USER` → gọi API người dùng sẽ 403 → `forkJoin` fail **toàn bộ**
> → Dashboard trắng. Đây là lỗi **đã từng xảy ra** và đã sửa bằng endpoint tổng hợp.

---

### UC-D02 — Đổi khoảng thời gian
**Actor:** Admin/Staff

**Luồng chính:**
1. Người dùng bấm một mốc: **Hôm nay · 7 ngày · 30 ngày · Tháng này**.
2. FE gọi lại endpoint với `range` tương ứng.
3. Số liệu theo kỳ (doanh thu kỳ, chuỗi biểu đồ, top bán chạy) **đổi theo**; các con số
   không phụ thuộc thời gian (tổng sản phẩm, tổng danh mục) **giữ nguyên**.

**Luồng phụ:**
- *Đang tải* → hiện skeleton đúng cấu trúc, **không** thay cả trang bằng spinner.

> **BR-D02 — "Hôm nay" là kỳ đặc biệt.** Với `range = TODAY`, kỳ so sánh là **hôm qua**
> (cùng độ dài 1 ngày). Với các kỳ còn lại, kỳ so sánh là **kỳ liền trước cùng độ dài**.

---

### UC-D03 — Làm mới số liệu
**Actor:** Admin/Staff · Nút **"Làm mới"**

Gọi lại endpoint với **đúng kỳ đang chọn**; nút bị `disabled` trong lúc tải để tránh bấm
liên tiếp.

---

### UC-D04 — Điều hướng nhanh từ thẻ KPI
**Actor:** Admin/Staff

Hai thẻ **bấm được** (có `role="button"`, hỗ trợ Enter/Space):

| Thẻ | Điều hướng tới |
|---|---|
| **Sản phẩm sắp hết** | `/admin/products` (lọc tồn kho thấp nếu có hỗ trợ) |
| **Cần xử lý ngay** | `/admin/orders?status=PENDING` |

> **BR-D03 — Chỉ thẻ có đích rõ ràng mới bấm được.** Các thẻ còn lại là **tĩnh** (không
> con trỏ, không hover ring) — tránh gợi ý sai rằng bấm được.

---

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| **BR-D01** | Dashboard dùng **một endpoint duy nhất** — không `forkJoin` nhiều API |
| **BR-D02** | Kỳ so sánh = kỳ liền trước **cùng độ dài**; riêng "Hôm nay" so với **hôm qua** |
| **BR-D03** | Chỉ thẻ KPI có đích điều hướng mới bấm được |
| **BR-D04** | **Doanh thu = tổng `totalPrice` của đơn `COMPLETED`** — đơn huỷ/đang giao **không** tính |
| **BR-D05** | **Top bán chạy** cũng tính trên đơn `COMPLETED`, xếp theo **số lượng** giảm dần, lấy **5** |
| **BR-D06** | **Không** trả số liệu người dùng cho người thiếu `READ_USER` (`userCount`, `activeUserCount`, `newCustomerCount` = `null`) |
| **BR-D07** | Thẻ **Khách hàng** và nút **Xuất báo cáo** ẩn với người thiếu `READ_USER` |
| **BR-D08** | Khoảng thời gian không hợp lệ (`from > to`) → lỗi `INVALID_DASHBOARD_RANGE` (4501) |
| **BR-D09** | Mọi con số là **số nguyên đồng (VND)**; phần trăm làm tròn **1 chữ số thập phân** |
| **BR-D10** | Endpoint **chỉ đọc** (`@Transactional(readOnly = true)`), không ghi bất kỳ bảng nào |
| **BR-D11** | **Xuất báo cáo chỉ ADMIN** — yêu cầu quyền `READ_USER`; STAFF không xuất được |
| **BR-D12** | **Chi phí khuyến mại** = Σ promotionDiscount + Σ voucherDiscount + Σ flashSaleDiscount, trên đơn **KHÔNG huỷ** |
| **BR-D13** | **Flash sale discount** = `(originalPrice − price) × quantity`, dùng `COALESCE(originalPrice, price)` cho đơn cũ (V15) |
| **BR-D14** | **Top chương trình/voucher** xếp theo **tiền đã giảm** giảm dần, lấy **5** |
| **BR-D15** | Tỉ lệ đơn có khuyến mại: tổng đơn = 0 → **`null`** (không chia 0) |

> **BR-D04 — Vì sao doanh thu chỉ tính đơn `COMPLETED`?**
> Đơn `PENDING`/`CONFIRMED`/`SHIPPING` **chưa chắc thu được tiền**; đơn `CANCELLED` thì
> chắc chắn không. Đếm cả đơn chưa hoàn thành sẽ **thổi phồng** doanh thu. Quy ước này
> **khớp với trang Đơn hàng** (`OrderStatsResponse.completedRevenue` dùng đúng
> `sumTotalPriceByStatus(COMPLETED)`) — hai màn hình phải cho **cùng một con số**.

> **BR-D05 — Top bán chạy ≠ `Product.sold`.**
> `Product.sold` là **bộ đếm luỹ kế từ trước tới nay** (tăng ngay khi tạo đơn, gồm cả đơn
> chưa giao). Top bán chạy của Dashboard là **theo kỳ đang chọn** và **chỉ đơn hoàn thành**
> → hai con số **khác nhau là đúng**. Nhãn trên UI ghi rõ *"đơn hoàn thành · theo kỳ"* để
> người dùng không so nhầm với cột "Đã bán" ở trang Sản phẩm.

> **BR-D06/BR-D07 — Vì sao ẩn số liệu người dùng với STAFF?**
> STAFF **không có** `READ_USER` (xem [README.md](README.md) §7). Trả số liệu người dùng
> cho STAFF là **rò rỉ thông tin** vượt quyền. Bảo vệ **hai lớp**: BE **không trả** (BR-D06)
> + FE **không hiện** (BR-D07).

---

## 5. Luồng nghiệp vụ

```
┌─ NGƯỜI DÙNG (Admin hoặc Staff) ───────────────────────────────────────┐
│  Mở /admin/dashboard                                                  │
│      │                                                                │
│      ▼                                                                │
│  GET /api/v1/admin/dashboard/stats?range=LAST_30_DAYS                 │
│      │                                                                │
│      ▼                                                                │
│  BE tổng hợp 1 lượt:                                                  │
│    · Doanh thu kỳ + kỳ trước  → % thay đổi                            │
│    · Đếm đơn theo 5 trạng thái · đơn cần xử lý                        │
│    · Chuỗi doanh thu theo NGÀY (kỳ + kỳ trước)                        │
│    · Top 5 sản phẩm bán chạy (đơn COMPLETED)                          │
│    · Tồn kho thấp · voucher đang chạy · danh mục · sản phẩm           │
│    · Số liệu NGƯỜI DÙNG  → CHỈ khi có READ_USER, ngược lại = null      │
│      │                                                                │
│      ▼                                                                │
│  FE dựng giao diện + GATE theo quyền:                                 │
│    · Thiếu READ_USER → ẩn thẻ "Khách hàng" + nút "Xuất báo cáo"       │
│    · Thiếu READ_DASHBOARD (403) → empty-state "quyền bị thu hồi"      │
│      │                                                                │
│      ▼                                                                │
│  Người dùng đổi kỳ  →  gọi lại endpoint  →  vẽ lại số + biểu đồ       │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Dictionary

> **Dashboard KHÔNG có bảng riêng.** Nó đọc tổng hợp từ các bảng đã tồn tại. Phần này liệt
> kê **nguồn dữ liệu** và **chỉ mục đề xuất**.

### 6.1 Nguồn dữ liệu (chỉ đọc)

| Số liệu | Bảng nguồn | Điều kiện |
|---|---|---|
| Doanh thu kỳ | `orders` | `status = COMPLETED` và `order_date` trong kỳ |
| Chuỗi doanh thu theo ngày | `orders` | như trên, nhóm theo `DATE(order_date)` |
| Đếm đơn theo trạng thái | `orders` | `GROUP BY status` |
| Đơn cần xử lý | `orders` | `status = PENDING` |
| Đơn hôm nay | `orders` | `order_date >= đầu ngày hôm nay` |
| Top 5 bán chạy | `order_detail` ⋈ `orders` | `orders.status = COMPLETED`, kỳ; `SUM(quantity)` giảm dần |
| Tồn kho thấp | `products` | `quantity < 5`, `deleted_at IS NULL` |
| Sản phẩm | `products` | `deleted_at IS NULL`; đếm thêm `active = true/false` |
| Danh mục | `categories` | đếm tổng + `active = true` |
| Voucher đang chạy | `vouchers` | `active = true` và trong khoảng `start_date..expiry_date` |
| Voucher sắp hết hạn | `vouchers` | như trên và `expiry_date <= now + 7 ngày` |
| Khách hàng *(chỉ khi có `READ_USER`)* | `users` | `deleted_at IS NULL`; mới = `created_at >= now - 7 ngày` |

### 6.2 Chỉ mục đề xuất (migration `V13__dashboard_indexes.sql`)

| Bảng | Chỉ mục | Phục vụ |
|---|---|---|
| `orders` | `(status, order_date)` | Doanh thu + chuỗi theo ngày + đếm theo trạng thái |
| `order_detail` | `(product_id)` | Top bán chạy (nhóm theo sản phẩm) |
| `vouchers` | `(active, expiry_date)` | Đếm voucher đang chạy / sắp hết hạn |

> Chỉ mục là **tối ưu**, không phải điều kiện đúng đắn. Nếu bảng còn nhỏ, Dashboard vẫn
> chạy đúng mà không cần chỉ mục — nhưng thêm sớm để tránh full-scan khi dữ liệu lớn lên.

### 6.3 Enum `DashboardRange`

| Giá trị | Nghĩa | Kỳ | Kỳ so sánh |
|---|---|---|---|
| `TODAY` | Hôm nay | 00:00 hôm nay → hiện tại | Cả ngày hôm qua |
| `LAST_7_DAYS` | 7 ngày | 7 ngày gần nhất (gồm hôm nay) | 7 ngày liền trước |
| `LAST_30_DAYS` | 30 ngày *(mặc định)* | 30 ngày gần nhất | 30 ngày liền trước |
| `THIS_MONTH` | Tháng này | Ngày 1 tháng này → hiện tại | Cùng số ngày của tháng trước |

---

## 7. Phân quyền (ma trận chi tiết)

> Đây là phần **cốt lõi** của tài liệu này. Bảng này có thể **tách riêng để phát cho
> Staff và Admin**.

### 7.1 Ma trận khối giao diện

| Khối trên Dashboard | Admin | Staff | Căn cứ |
|---|:--:|:--:|---|
| Thẻ **Doanh thu hôm nay** (+ % so hôm qua) | ✅ | ✅ | STAFF là người bán hàng — xem [[role-permission-model]] |
| Thẻ **Đơn hàng mới** | ✅ | ✅ | Có `READ_ORDER` |
| Thẻ **Khách hàng** | ✅ | ❌ | STAFF **không có** `READ_USER` (BR-D06/07) |
| Thẻ **Sản phẩm** | ✅ | ✅ | Có `READ_PRODUCT` |
| Thẻ **Voucher đang chạy** | ✅ | ✅ | Có `READ_VOUCHER` |
| Thẻ **Danh mục** | ✅ | ✅ | Có `READ_CATEGORY` |
| Thẻ **Sản phẩm sắp hết** *(bấm được)* | ✅ | ✅ | Có `READ_PRODUCT` |
| Thẻ **Cần xử lý ngay** *(bấm được)* | ✅ | ✅ | Có `READ_ORDER` |
| Thanh **lọc khoảng thời gian** | ✅ | ✅ | — |
| Biểu đồ **Doanh thu** (area, **số tiền**) | ✅ | ✅ | Nhất quán với trang Đơn hàng |
| Biểu đồ **trạng thái đơn** (donut) | ✅ | ✅ | Có `READ_ORDER` |
| **Top 5 sản phẩm bán chạy** | ✅ | ✅ | Dữ liệu sản phẩm + đơn hàng |
| **Danh sách sắp hết hàng** | ✅ | ✅ | Có `READ_PRODUCT` |
| Nút **Xuất báo cáo** | ✅ | ❌ | Báo cáo tổng hợp dành cho quản trị (BR-D07) |
| Nút **Làm mới** | ✅ | ✅ | — |
| Khối **Hoạt động gần đây** | — | — | **Đã bỏ** (ngoài phạm vi §1.4) |

### 7.2 Căn cứ kỹ thuật

| Lớp | Cơ chế |
|---|---|
| **Route** | `/admin` khai `data: { roles: ['ADMIN','STAFF'] }` — CUSTOMER bị chặn ở route cha |
| **Endpoint** | `@PreAuthorize("hasAuthority('READ_DASHBOARD')")` — cả Admin và Staff đều có |
| **Số liệu người dùng** | BE trả `null` khi người gọi thiếu `READ_USER` (BR-D06) |
| **Giao diện** | FE `authService.hasPermission('READ_USER')` để ẩn thẻ + nút (BR-D07) |

> **Vì sao không tạo quyền `READ_DASHBOARD_REVENUE` riêng?** Vì STAFF **đã** thấy doanh thu
> ở trang Đơn hàng (`completedRevenue`) — thêm quyền mới sẽ **mâu thuẫn** với thực tế đó,
> và làm phức tạp ma trận quyền mà không giải quyết vấn đề gì. Nhất quán **quan trọng hơn**
> chi tiết hoá thừa.

### 7.3 Ma trận API

| Method | Path | Quyền | Admin | Staff |
|---|---|---|:--:|:--:|
| GET | `/api/v1/admin/dashboard/stats` | `READ_DASHBOARD` | ✅ | ✅ |
| GET | `/api/v1/admin/dashboard/export` | `READ_USER` | ✅ | ❌ 403 |

> Cùng một endpoint `/stats` cho cả hai vai trò; **nội dung** trả về khác nhau ở nhóm số
> liệu người dùng (BR-D06). `/export` tách riêng vì báo cáo là **quyền quản trị** (BR-D11).

---

## 8. API Contract

### 8.1 `GET /api/v1/admin/dashboard/stats`

**Quyền:** `READ_DASHBOARD` · **Chỉ đọc**

| Tham số | Kiểu | Bắt buộc | Mặc định | Ghi chú |
|---|---|:--:|---|---|
| `range` | `DashboardRange` | | `LAST_30_DAYS` | `TODAY` \| `LAST_7_DAYS` \| `LAST_30_DAYS` \| `THIS_MONTH` |

**`DashboardStatsResponse`** (mở rộng từ `DashboardStats` hiện tại):

| Nhóm | Trường | Kiểu | Ghi chú |
|---|---|---|---|
| **Người dùng** | `userCount` | `Long` | `null` nếu thiếu `READ_USER` |
| | `activeUserCount` | `Long` | `null` nếu thiếu `READ_USER` |
| | `newCustomerCount` | `Long` | Mới trong 7 ngày; `null` nếu thiếu `READ_USER` |
| **Sản phẩm** | `productCount` | `Long` | Tổng (chưa xoá mềm) |
| | `activeProductCount` | `Long` | `active = true` |
| | `inactiveProductCount` | `Long` | `active = false` |
| | `lowStockCount` | `Long` | `quantity < 5` |
| | `criticalStockCount` | `Long` | `quantity < 2` |
| | `lowStockProducts` | `List<LowStockProduct>` | Tối đa 5, tồn tăng dần |
| **Danh mục** | `categoryCount` | `Long` | Tổng |
| | `activeCategoryCount` | `Long` | `active = true` |
| **Voucher** | `voucherCount` | `Long` | Tổng |
| | `activeVoucherCount` | `Long` | Đang chạy trong khoảng thời gian |
| | `voucherExpiringSoonCount` | `Long` | Hết hạn trong 7 ngày |
| **Đơn hàng** | `totalOrderCount` | `Long` | Tổng mọi trạng thái |
| | `needsActionCount` | `Long` | `PENDING` |
| | `ordersTodayCount` | `Long` | Đặt trong ngày hôm nay |
| | `ordersByStatus` | `Map<String, Long>` | 5 trạng thái → số đếm |
| **Doanh thu** | `revenueInRange` | `Long` | Kỳ đang chọn |
| | `revenuePrevRange` | `Long` | Kỳ liền trước |
| | `revenueChangePercent` | `Double` | `(kỳ − trước) / trước × 100`; `trước = 0` → `null` |
| | `revenueToday` | `Long` | Riêng cho thẻ "Doanh thu hôm nay" |
| | `revenueYesterday` | `Long` | Để tính % thẻ đó |
| **Chuỗi** | `revenueSeries` | `List<DailyPoint>` | `{ date, revenue, orderCount }` theo ngày |
| | `previousRevenueSeries` | `List<DailyPoint>` | Kỳ trước, cùng độ dài, để vẽ đường so sánh |
| **Top** | `topSellingProducts` | `List<TopProduct>` | 5 dòng: `{ productId, name, code, image, quantitySold, revenue }` |

> **`revenueChangePercent` khi kỳ trước = 0:** trả `null` (không chia cho 0). FE hiển thị
> pill trung tính "—" thay vì "∞%" hay "NaN%".

### 8.2 Bảng mã lỗi

| Code | Hằng số | HTTP | Thông báo |
|---|---|---|---|
| 4501 | `INVALID_DASHBOARD_RANGE` | 400 | Khoảng thời gian không hợp lệ |

> Không phát sinh mã lỗi mới nào khác: 403 thiếu quyền đã có sẵn ở tầng security.

### 8.3 `GET /api/v1/admin/dashboard/export`

**Quyền:** `READ_USER` (chỉ ADMIN — BR-D11) · **Chỉ đọc** · **Trả về FILE nhị phân**

| Tham số | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `range` | `DashboardRange` | `LAST_30_DAYS` | Như `/stats` |

**Response:** `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
`Content-Disposition: attachment; filename="bao-cao-YYYYMMDD-HHmmss.xlsx"`.

**File gồm 4 sheet:**

| Sheet | Nội dung |
|---|---|
| **Tổng quan** | Doanh thu (kỳ này/kỳ trước/%/hôm nay/hôm qua) · Đơn hàng (tổng, cần xử lý, hôm nay, theo trạng thái) · Sản phẩm & danh mục · Voucher · Người dùng *(chỉ khi có `READ_USER`)* |
| **Doanh thu theo ngày** | Ngày · Doanh thu · Số đơn |
| **Top sản phẩm** | Hạng · Mã · Tên · Đã bán · Doanh thu |
| **Khuyến mại** | Chi phí (tổng/promotion/voucher/flash) · Tỉ lệ đơn có ưu đãi · Top chương trình · Top voucher · Flash sale |

> **Số liệu trong file LẤY TỪ CÙNG hàm `DashboardService.getStats`** mà màn hình dùng —
> file xuất ra luôn khớp đúng thứ admin đang nhìn. Không viết lại truy vấn riêng.

---

## 9. Màn hình & tương tác

### 9.1 Bố cục (theo mockup đã duyệt)

| Vùng | Nội dung |
|---|---|
| **Header** | Tiêu đề "Bảng điều khiển" + chip vai trò + nút **Làm mới** + nút **Xuất báo cáo** *(chỉ Admin)* |
| **Hàng KPI 1** | Doanh thu hôm nay · Đơn hàng mới · Khách hàng *(chỉ Admin)* · Sản phẩm |
| **Hàng KPI 2** | Voucher đang chạy · Danh mục · Sắp hết hàng *(bấm được)* · Cần xử lý ngay *(bấm được)* |
| **Thanh lọc kỳ** | Segmented: Hôm nay / 7 ngày / 30 ngày / Tháng này + nhãn "So sánh với kỳ trước" |
| **Biểu đồ** | Trái: **Doanh thu** (area chart, có đường kỳ trước + tooltip). Phải: **donut trạng thái đơn** + danh sách chú giải |
| **Danh sách** | Trái: **Top 5 bán chạy**. Phải: **Sắp hết hàng** |
| **Khuyến mại** | Trái: **Chi phí khuyến mại** (tổng + tách promotion/voucher/flash + tỉ lệ đơn có ưu đãi). Phải: **Chương trình hiệu quả** (top promotion, kèm khối Top voucher) |

### 9.4 Khối "Hiệu quả khuyến mại" (chi tiết)

| Thành phần | Nội dung |
|---|---|
| **Tổng chi phí** | `Σ promotion + Σ voucher + Σ flash`, đơn **không huỷ** |
| **Tỉ lệ đơn có ưu đãi** | `số đơn có ưu đãi / tổng đơn` — hiện `—` khi tổng đơn = 0 |
| **Danh sách chi phí** | 3 dòng màu semantic: Promotion (primary) · Voucher (warning) · Flash Sale (danger) |
| **Flash sale** | Dòng phụ: số máy đã bán + số đơn |
| **Chương trình hiệu quả** | Xếp hạng theo tiền đã giảm, có huy hiệu hạng 1/2/3; bên dưới là khối **Top voucher** (mã + số đơn + tiền giảm) |
| **Trạng thái trống** | Không có chương trình nào áp → empty-state nhẹ "Không có chương trình nào được áp trong khoảng này" |

> **Đơn HUỶ không tính vào chi phí khuyến mại** (BR-BL14a) — vì tiền giảm trên đơn huỷ
> không phải chi phí thật. Truy vấn loại `CANCELLED` ngay ở tầng DB.

### 9.2 Trạng thái bắt buộc

| Trạng thái | Xử lý |
|---|---|
| **Đang tải** | Skeleton đúng cấu trúc (8 thẻ + 2 khối biểu đồ) — **không** spinner toàn trang |
| **403 (thiếu quyền)** | Empty-state variant `permission`, icon `lock`, "Quyền xem bảng điều khiển đã bị thu hồi" |
| **Lỗi mạng** | Empty-state lỗi + nút **Thử lại**; không hiện stack trace |
| **DB trống** | Số = 0, biểu đồ trục rỗng + "Chưa có dữ liệu", danh sách hiện empty-state nhẹ |

### 9.3 Biểu đồ (ApexCharts)

| Biểu đồ | Loại | Ghi chú |
|---|---|---|
| **Doanh thu** | `area` | 2 series: kỳ này (đậm) + kỳ trước (nhạt, nét đứt). Trục Y rút gọn (`12tr`). Tooltip: ngày · doanh thu · số đơn. Tôn trọng `prefers-reduced-motion` (tắt animation) |
| **Trạng thái đơn** | `donut` | 5 phần theo màu semantic: Hoàn thành `success` · Đã xác nhận `primary` · Đang giao `indigo` · Chờ xác nhận `warning` · Đã hủy `danger`. Tâm donut ghi tổng đơn |

> **Màu biểu đồ lấy từ token DESIGN.md**, không hardcode hex trong component. Dark mode:
> ApexCharts nhận bảng màu theo theme hiện hành.

---

## 10. Edge case

| # | Tình huống | Xử lý |
|---|---|---|
| E-D01 | Kỳ trước có doanh thu = 0 | `revenueChangePercent = null`; FE hiện pill "—", không chia 0 |
| E-D02 | DB chưa có đơn nào | Mọi số = 0; biểu đồ trục rỗng; không vỡ layout |
| E-D03 | Kỳ chọn chỉ có 1 ngày (`TODAY`) | Chuỗi có 1 điểm; area chart vẫn vẽ được (điểm + đường phẳng) |
| E-D04 | STAFF gọi endpoint | Trả 200, nhưng `userCount`/`activeUserCount`/`newCustomerCount` = `null` |
| E-D05 | STAFF cố mở `/admin/users` | `adminGuard` chặn (thiếu `READ_USER`) → không vào được |
| E-D06 | CUSTOMER gọi API dashboard | 403 (không có `READ_DASHBOARD`) |
| E-D07 | Sản phẩm sắp hết < 5 dòng | Danh sách hiện đúng số có; nếu 0 → empty-state "Đủ hàng" |
| E-D08 | Kỳ trước dài hơn kỳ này (`THIS_MONTH` đầu tháng) | Kỳ trước lấy **cùng số ngày**, không lấy cả tháng |
| E-D09 | Nhiều người mở Dashboard cùng lúc | Chỉ đọc → không khoá bảng, không ảnh hưởng nhau |
| E-D10 | Đơn `COMPLETED` nhưng `totalPrice` NULL (dữ liệu cũ) | `COALESCE(SUM(total_price), 0)` |

---

## 11. Acceptance Criteria

| Mã | Tiêu chí |
|---|---|
| **AC-D01** | `GET /dashboard/stats` trả đủ trường trong §8.1; mặc định `range = LAST_30_DAYS` |
| **AC-D02** | Doanh thu = tổng `totalPrice` đơn `COMPLETED`; **khớp** `completedRevenue` của trang Đơn hàng |
| **AC-D03** | Đổi `range` → `revenueInRange`, chuỗi, top bán chạy **đổi theo**; tổng sản phẩm/danh mục **không đổi** |
| **AC-D04** | Kỳ trước = 0 → `revenueChangePercent = null`; FE hiện "—", **không** NaN/∞ |
| **AC-D05** | STAFF gọi endpoint → `userCount`, `activeUserCount`, `newCustomerCount` = **`null`** |
| **AC-D06** | ADMIN gọi endpoint → 3 trường trên có giá trị |
| **AC-D07** | FE: STAFF **không** thấy thẻ "Khách hàng" và nút "Xuất báo cáo"; ADMIN thấy cả hai |
| **AC-D08** | Thiếu `READ_DASHBOARD` → 403 → FE hiện empty-state "quyền bị thu hồi" |
| **AC-D09** | Không còn chuỗi/dữ liệu mẫu nào trong Dashboard (không `buildSampleActivity`, không "ORD-2024") |
| **AC-D10** | Biểu đồ doanh thu là ApexCharts `area`; donut trạng thái đủ 5 phần; tâm ghi tổng đơn |
| **AC-D11** | Bấm thẻ "Cần xử lý ngay" → `/admin/orders?status=PENDING`; thẻ "Sắp hết hàng" → `/admin/products` |
| **AC-D12** | Thẻ không có đích **không** bấm được (không `cursor:pointer`, không hover ring) |
| **AC-D13** | DB trống → số = 0, biểu đồ không vỡ layout |
| **AC-D14** | Dark mode: biểu đồ + thẻ đọc được, màu theo token |
| **AC-D15** | Mobile: lưới KPI 1→2 cột; biểu đồ không tràn ngang |
| **AC-D16** | `prefers-reduced-motion` → biểu đồ không chạy animation |
| **AC-D17** | Endpoint chỉ đọc — gọi 10 lần không đổi bất kỳ bản ghi nào |
| **AC-D18** | Số liệu voucher "đang chạy" tôn trọng `start_date`/`expiry_date`, không chỉ `active` |
| **AC-D19** | `GET /dashboard/export` trả file `.xlsx` hợp lệ, đủ 4 sheet, tên file có timestamp |
| **AC-D20** | STAFF gọi `/export` → **403**; nút "Xuất báo cáo" **không hiện** với STAFF |
| **AC-D21** | Số liệu trong file xuất **khớp** số liệu trên màn hình cùng kỳ |
| **AC-D22** | Chi phí khuyến mại **loại** đơn `CANCELLED` |
| **AC-D23** | Flash sale discount dùng `COALESCE(originalPrice, price)` — đơn cũ (originalPrice NULL) không ra số âm |
| **AC-D24** | Tổng đơn kỳ = 0 → tỉ lệ đơn có ưu đãi = `—`, không chia 0 |

---

## 12. Truy vết

| BR | UC | AC | Code (dự kiến) |
|---|---|---|---|
| BR-D01 | UC-D01 | AC-D01 | `DashboardService.getStats` (1 endpoint) |
| BR-D02 | UC-D02 | AC-D03, AC-D04 | `DashboardRange` + `DashboardService.resolvePeriod` |
| BR-D03 | UC-D04 | AC-D11, AC-D12 | `dashboard.html` (thẻ `clickable`) |
| BR-D04 | UC-D01 | AC-D02 | `OrderRepository.sumTotalPriceByStatus(COMPLETED)` |
| BR-D05 | UC-D01 | AC-D10 | `OrderDetailRepository.findTopSelling` |
| BR-D06 | UC-D01 | AC-D05, AC-D06 | `DashboardService` (kiểm `READ_USER` → `null`) |
| BR-D07 | UC-D01 | AC-D07 | `dashboard.component.ts` `canViewCustomers` |
| BR-D08 | UC-D02 | AC-D01 | `INVALID_DASHBOARD_RANGE` (4501) |
| BR-D09 | — | AC-D02 | `Math.round(x * 10) / 10.0` |
| BR-D10 | — | AC-D17 | `@Transactional(readOnly = true)` |
| BR-D11 | — | AC-D19, AC-D20 | `DashboardController.export` (`hasAuthority('READ_USER')`) |
| BR-D12 | — | AC-D22 | `OrderRepository.sumDiscountCost` + `sumFlashSaleEffect` |
| BR-D13 | — | AC-D23 | `sumFlashSaleEffect` (`COALESCE(od.originalPrice, od.price)`) |
| BR-D14 | — | AC-D21 | `DashboardService.topPromotions` / `topVouchers` |
| BR-D15 | — | AC-D24 | `DashboardService.buildPromotionEffect` |

---

## 13. Trạng thái triển khai

| Hạng mục | Trạng thái |
|---|---|
| BA doc (tài liệu này) | ✅ 2026-10-03 |
| Mockup `admin-dashboard-preview.html` | ✅ đã duyệt (có toggle Admin/Staff) |
| Backend — DTO + service + query + migration V17 + **export Excel** | ✅ 2026-10-03 |
| Frontend — ApexCharts + bố cục mới + gating + **khối khuyến mại** + **nút xuất** | ✅ 2026-10-03 |
| Test — BE 275 pass (16 test Dashboard) · FE build 0 lỗi | ✅ 2026-10-03 |

**Quyết định đã chốt (2026-10-03):**

1. Biểu đồ dùng **ApexCharts**.
2. **Bỏ** khối "Hoạt động gần đây".
3. **STAFF được thấy doanh thu** (nhất quán trang Đơn hàng).
4. Thẻ **"Khách hàng" ẩn hẳn** với STAFF.
5. Biểu đồ doanh thu hiện **số tiền** với cả hai vai trò.
6. **Top bán chạy: STAFF được thấy**; **Xuất báo cáo: chỉ ADMIN**.
7. **Xuất báo cáo = Excel `.xlsx`** (Apache POI), nội dung **tổng hợp theo kỳ**, 4 sheet.
8. **Thống kê khuyến mại đặt trên Dashboard** (không tách trang riêng).
9. **Bỏ banner** khỏi báo cáo (hệ thống chưa có tracking view/click).

**Việc còn lại:** chạy UI thật (`ng serve` + đăng nhập) để xem biểu đồ và bấm "Xuất báo cáo"
tải file — phần API/DB đã verify bằng curl.
