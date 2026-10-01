# 04 — HOME BANNER (Slide trang chủ)

> Tài liệu nghiệp vụ · v1.0 · 2026-09-27
> Đọc kèm: [README.md](README.md) · [03-flash-sale.md](03-flash-sale.md)

---

## 1. Mục tiêu & phạm vi

### 1.1 Mục tiêu

Cho phép admin **quản lý carousel trang chủ** — thêm/sửa/ẩn slide, đổi thứ tự, gắn liên
kết — mà **không cần deploy lại**. Trước đây hero trang chủ **hardcode** trong
`home.component.html`, muốn đổi nội dung khuyến mại phải sửa code.

### 1.2 Trong phạm vi

- Admin CRUD slide: tiêu đề, nhãn nhỏ (kicker), mô tả phụ, ảnh, loại liên kết, thứ tự,
  bật/tắt.
- Khách xem carousel tự chạy ở trang chủ, bấm slide → điều hướng theo loại liên kết.
- Giới hạn tối đa **5 slide** bật cùng lúc.

### 1.3 Ngoài phạm vi

- **Loại liên kết "URL tự do"** — đã **bỏ hẳn** (xem BR-B03).
- Banner flash sale đếm ngược — thuộc [03-flash-sale.md](03-flash-sale.md), không phải
  `HomeBanner`.

> **Banner là khối TRÌNH BÀY, không đụng logic tiền.** Không có trường nào ảnh hưởng
> đến giá, giảm giá, hay đơn hàng.

---

## 2. Actor

| Actor | Mô tả | Quyền |
|---|---|---|
| **Admin** | Quản trị viên | Toàn quyền banner |
| **Staff** | Nhân viên | Tạo/xem/sửa — **không xoá** |
| **Khách (kể cả chưa đăng nhập)** | Người xem trang chủ | Chỉ xem |

---

## 3. Use Case

### UC-B01 — Admin tạo slide banner
**Actor:** Admin/Staff · **Tiền điều kiện:** `CREATE_HOME_BANNER`

**Luồng chính:**
1. Admin mở form thêm slide.
2. Nhập: nhãn nhỏ (kicker), tiêu đề, mô tả phụ, ảnh slide, loại liên kết + giá trị,
   thứ tự hiển thị, công tắc hiển thị.
3. Hệ thống kiểm tra: tiêu đề không rỗng, có ảnh, chọn loại liên kết, giá trị liên kết
   **trỏ tới thực thể có thật**, và **số slide đang bật < 5**.
4. Lưu slide.

**Luồng phụ:**
- *Tiêu đề rỗng* → `BANNER_TITLE_REQUIRED` (4402).
- *Không có ảnh* → `BANNER_IMAGE_REQUIRED` (4403).
- *Chưa chọn loại/giá trị liên kết* → `BANNER_TARGET_REQUIRED` (4404).
- *Đích không tồn tại / danh mục đã tắt* → `INVALID_BANNER_TARGET` (4406).
- *Đã có 5 slide bật, bật thêm* → `BANNER_ACTIVE_LIMIT_EXCEEDED` (4405).

> **BR-B01 — Ảnh bắt buộc.** Slide luôn hiển thị bằng ảnh; nền do scrim gradient ở FE lo.
> Vì vậy kịch bản "chưa có ảnh thì dùng màu nền" **không bao giờ xảy ra**.

> **BR-B02 — Tối đa 5 slide bật cùng lúc**, khớp số chấm của carousel trang chủ. Khi sửa
> một slide đang bật, chính nó **không tính** vào con số 5.

---

### UC-B02 — Admin cập nhật slide
**Actor:** Admin/Staff · **Tiền điều kiện:** `UPDATE_HOME_BANNER`

- Xử lý ảnh theo thứ tự ưu tiên: **file mới** > **URL mới** > cờ **xoá ảnh** > giữ nguyên.
- Kiểm tra lại trần 5 slide nếu slide được bật.
- Nếu slide đang tắt mà form gửi `active = true` → phải kiểm tra trần.

---

### UC-B03 — Admin bật/tắt / xoá slide
**Actor:** Admin/Staff

- **Bật/tắt** (`PATCH /{id}/status`): đổi công tắc ngay trên thẻ ở màn danh sách. Bật →
  kiểm tra trần 5.
- **Xoá** (`DELETE`, quyền `DELETE_HOME_BANNER`): xoá **mềm** + xoá ảnh Cloudinary.

---

### UC-B04 — Khách xem carousel trang chủ
**Actor:** Khách (không cần đăng nhập) · **Endpoint:** `GET /client/home-banners`

**Luồng chính:**
1. FE gọi endpoint (public).
2. BE trả về danh sách slide `active = true`, sắp theo `sortOrder` tăng dần.
3. FE dựng carousel: tự chạy 6 giây/slide, có nút trước/sau + chấm điều hướng khi >1 slide.
4. **Tạm dừng khi hover/focus** và **tôn trọng `prefers-reduced-motion`**.
5. Bấm slide → điều hướng theo `targetType`:
   - `PRODUCT` → `/products/:code`
   - `CATEGORY` → `/products?categoryId=...`
   - `BRAND` → `/products?factory=...`
   - `FLASH_SALE` → `/flash-sale`

**Luồng phụ:**
- *Không có banner nào* → FE hiển thị hero mặc định (không vỡ layout).

---

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| **BR-B01** | Ảnh slide là **bắt buộc** |
| **BR-B02** | Tối đa **5 slide** bật cùng lúc |
| **BR-B03** | **KHÔNG có loại liên kết "URL tự do"** — mọi đích là thực thể có thật |
| **BR-B04** | `targetValue` phải **trỏ tới thực thể đang tồn tại và dùng được** |
| **BR-B05** | `PRODUCT` lưu **code** sản phẩm (không phải id) — vì route khách là `/products/:code` |
| **BR-B06** | `BRAND` không có bảng riêng — kiểm tra có sản phẩm nào mang hãng đó |
| **BR-B07** | `CATEGORY` phải đang **active** — banner dẫn tới danh mục đã tắt là đích chết |

> **BR-B03 — Vì sao bỏ "URL tự do"?** Trước đây admin phải **gõ tay đường dẫn nội bộ mà
> không biết nhập gì**; đích là thực thể có thật thì nên **chọn từ danh sách** và validate
> được tồn tại. Bỏ URL cũng **xoá luôn đường cho link ngoài / `javascript:`** lọt vào banner
> — một lỗ hổng bảo mật tiềm ẩn.

> **BR-B04 — Validate đích tồn tại khi lưu:**
> - `PRODUCT` → tra `Product` theo code, không có → lỗi.
> - `CATEGORY` → tra `Category`, không có **hoặc `active = false`** → lỗi.
> - `FLASH_SALE` → tra `FlashSale`, không có → lỗi.
> - `BRAND` → kiểm tra có sản phẩm nào mang `factory` đó (không phân biệt hoa/thường).

---

## 5. Luồng nghiệp vụ

```
┌─ ADMIN ───────────────────────────────────────────────────────────────┐
│  Tạo slide: kicker + tiêu đề + mô tả phụ + ảnh (bắt buộc)             │
│  + loại liên kết + giá trị (chọn từ danh sách, KHÔNG gõ URL)          │
│  + thứ tự hiển thị + công tắc                                         │
│                        │                                              │
│                        ▼                                              │
│  Validate: tiêu đề ✓ · ảnh ✓ · đích tồn tại ✓ · đang bật < 5 ✓        │
│                        │                                              │
│                        ▼                                              │
│  Bật/tắt (kiểm tra trần 5)  ·  Xoá mềm + xoá ảnh Cloudinary           │
└────────────────────────┬──────────────────────────────────────────────┘
                         │
┌─ KHÁCH ────────────────▼──────────────────────────────────────────────┐
│  GET /client/home-banners  (public — chưa login vẫn thấy)             │
│      │                                                                │
│      ▼                                                                │
│  Danh sách slide active, sort theo sortOrder                          │
│      │                                                                │
│      ▼                                                                │
│  Carousel: tự chạy 6s · pause on hover/focus · prefers-reduced-motion │
│      │                                                                │
│      └─ bấm slide → PRODUCT /products/:code                           │
│                     CATEGORY /products?categoryId=                    │
│                     BRAND /products?factory=                          │
│                     FLASH_SALE /flash-sale                            │
│                                                                       │
│  Không có banner → hero mặc định (không vỡ layout)                    │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Dictionary

### 6.1 Bảng `home_banners` (entity `HomeBanner`)

| Cột | Kiểu | Null | Ý nghĩa |
|---|---|:--:|---|
| `id` | varchar(255) | ✗ | Khoá chính (UUID) |
| `title` | varchar(255) | ✗ | Tiêu đề slide |
| `kicker` | varchar(100) | ✓ | Nhãn nhỏ in hoa đứng trước tiêu đề (VD "BỘ SƯU TẬP MỚI") |
| `subtitle` | varchar(255) | ✓ | Mô tả phụ |
| `image` | varchar(255) | ✗ | Ảnh slide (URL Cloudinary) — **bắt buộc** |
| `target_type` | enum(`BRAND`,`CATEGORY`,`FLASH_SALE`,`PRODUCT`) | ✗ | Loại liên kết |
| `target_value` | varchar(255) | ✗ | Định danh theo `targetType` |
| `sort_order` | int | ✗ | Nhỏ hiển thị trước |
| `active` | bit(1) | ✗ | Hiển thị ở trang chủ |
| `created_at` / `updated_at` | datetime(6) | ✓ | Audit |
| `deleted_at` | datetime(6) | ✓ | Xoá mềm |

**Index:** `IDX_home_banners_active_sort (active, sort_order)`.

**Cột đã bị xoá:**
- `bg_color` (V6) — xem BR-B01.

### 6.2 Enum `BannerTargetType`

| Giá trị | Ý nghĩa | `targetValue` chứa gì | Route FE |
|---|---|---|---|
| `PRODUCT` | Trang chi tiết sản phẩm | `Product.code` | `/products/:code` |
| `CATEGORY` | Danh sách theo danh mục | `Category.id` | `/products?categoryId=` |
| `BRAND` | Danh sách theo hãng | `Product.factory` (VD "ASUS") | `/products?factory=` |
| `FLASH_SALE` | Trang flash sale | `FlashSale.id` | `/flash-sale` |

> **Đã xoá:** giá trị `URL` — xem BR-B03.

---

## 7. API Contract

### 7.1 Admin — `/api/v1/admin/home-banners`

| Method | Path | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/home-banners` | `READ_HOME_BANNER` | — | `List<HomeBannerResponse>` (gồm cả đang tắt) |
| GET | `/home-banners/{id}` | `READ_HOME_BANNER` | — | `HomeBannerResponse` |
| POST | `/home-banners` | `CREATE_HOME_BANNER` | `@ModelAttribute HomeBannerCreationRequest` (form-data) | `HomeBannerResponse` |
| PUT | `/home-banners/{id}` | `UPDATE_HOME_BANNER` | `@ModelAttribute HomeBannerCreationRequest` | `HomeBannerResponse` |
| DELETE | `/home-banners/{id}` | `DELETE_HOME_BANNER` | — | — |
| PATCH | `/home-banners/{id}/status` | `UPDATE_HOME_BANNER` | `{ active }` | `HomeBannerResponse` |

**`HomeBannerCreationRequest`:**

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|:--:|---|
| `title` | String | ✓ | |
| `kicker` | String | | |
| `subtitle` | String | | |
| `inputFile` | MultipartFile | | Ảnh slide |
| `imageUrl` | String | | Dán link ảnh |
| `removeImage` | boolean | | |
| `targetType` | BannerTargetType | ✓ | |
| `targetValue` | String | ✓ | |
| `sortOrder` | Integer | | Mặc định 0 |
| `active` | Boolean | | Mặc định true |

**`HomeBannerResponse`:** `id, title, kicker, subtitle, image, targetType, targetValue,
sortOrder, active, createdAt, updatedAt`.

---

### 7.2 Client — `/api/v1/client/home-banners` (**public, không cần token**)

| Method | Path | Trả về |
|---|---|---|
| GET | `/home-banners` | `List<HomeBannerResponse>` — chỉ slide `active`, sắp theo `sortOrder` |

> **Public là bắt buộc:** khách **chưa đăng nhập** vẫn phải thấy trang chủ có banner.

---

### 7.3 Bảng mã lỗi

| Code | Hằng số | HTTP | Thông báo |
|---|---|---|---|
| 4401 | `BANNER_NOT_FOUND` | 404 | Không tìm thấy banner trang chủ |
| 4402 | `BANNER_TITLE_REQUIRED` | 400 | Tiêu đề banner không được để trống |
| 4403 | `BANNER_IMAGE_REQUIRED` | 400 | Banner phải có ảnh |
| 4404 | `BANNER_TARGET_REQUIRED` | 400 | Banner chưa chọn nơi dẫn tới |
| 4405 | `BANNER_ACTIVE_LIMIT_EXCEEDED` | 400 | Chỉ được bật tối đa 5 slide cùng lúc — hãy tắt bớt một slide trước khi bật slide mới |
| 4406 | `INVALID_BANNER_TARGET` | 400 | Đối tượng của banner không tồn tại hoặc không hợp lệ |

---

## 8. Màn hình & tương tác

### 8.1 Admin

| Màn | Nội dung |
|---|---|
| **Danh sách Banner** (`/admin/home-banners`) | **Lưới card 2 cột** (không dùng bảng). Mỗi card: ảnh preview + badge `#sortOrder` + kicker/tiêu đề/mô tả phụ + meta (loại liên kết, giá trị, thứ tự) + toggle hiển thị + menu kebab (Sửa / Nhân bản / Xoá). Tiêu đề hiển thị "(N/5 đang bật)". Có card "Cách gắn liên kết cho slide" — bảng hướng dẫn 4 dòng PRODUCT/CATEGORY/BRAND/FLASH_SALE |
| **Form Banner** (`/admin/home-banners/create`, `/:id/edit`) | Nhãn nhỏ, tiêu đề, mô tả phụ; ảnh slide (**bắt buộc**, tỉ lệ 16:6); loại liên kết + giá trị (widget đổi theo loại: select danh mục / product-picker / select phiên / select hãng); thứ tự; toggle hiển thị. Panel **"Xem trước slide"** cập nhật trực tiếp khi gõ |

> **Widget `targetValue` đổi theo `targetType`** — đây là điểm mấu chốt của BR-B03: admin
> **chọn** đích từ danh sách, không gõ đường dẫn.

### 8.2 Client

| Thành phần | Nội dung |
|---|---|
| **`home-banner-carousel`** | Autoplay 6 giây, pause khi hover/focus, tôn trọng `prefers-reduced-motion`; nút trước/sau + chấm điều hướng khi >1 slide; mỗi slide là link theo `targetType`. Giữ nhận diện: gradient `--hero-bg`, `max-w-7xl`, text overlay đọc được cả 2 chế độ (scrim gradient trái→phải) |
| **Trang chủ** | Carousel lắp vào đầu trang, thay hero hardcode. Nếu không có banner → hiển thị hero mặc định. Các khối bên dưới (trust strip, carousel thương hiệu, grid danh mục, sản phẩm mới, bán chạy) **giữ nguyên** |

> **Không được kéo cả trang lỗi:** mỗi khối (banner / flash / danh mục / sản phẩm) tải
> **độc lập**; một khối lỗi thì **ẩn khối đó**, không chặn các khối khác. Trang chủ không
> dùng `forkJoin` gộp tất cả.

---

## 9. Edge case

| # | Tình huống | Xử lý |
|---|---|---|
| E-B01 | Banner dẫn tới sản phẩm đã bị xoá | Validate lúc lưu chặn; nếu xoá sau đó → link dẫn tới 404, admin cần sửa slide |
| E-B02 | Banner dẫn tới danh mục đã **tắt** | Chặn `INVALID_BANNER_TARGET` khi lưu (BR-B07) |
| E-B03 | Banner dẫn tới `BRAND` không có sản phẩm nào | Chặn `INVALID_BANNER_TARGET` (BR-B06) |
| E-B04 | Đã có 5 slide bật, admin bật slide thứ 6 | Chặn `BANNER_ACTIVE_LIMIT_EXCEEDED` |
| E-B05 | Admin **sửa** một slide đang bật (không đổi trạng thái) | **Không** bị chặn — chính nó không tính vào con số 5 |
| E-B06 | Admin gõ `javascript:alert(1)` vào `targetValue` | **Không có đường** — đã bỏ loại URL, `targetValue` phải là id/code thực thể có thật |
| E-B07 | Trang chủ có 0 banner (DB trống) | Hiển thị hero mặc định, không vỡ layout |
| E-B08 | API banner lỗi khi tải trang chủ | Ẩn khối carousel, các khối khác vẫn chạy |
| E-B09 | Người dùng bật `prefers-reduced-motion` | Carousel không tự chạy |
| E-B10 | Admin xoá slide | Xoá mềm + xoá ảnh Cloudinary |

---

## 10. Acceptance Criteria

| Mã | Tiêu chí |
|---|---|
| **AC-B01** | Tạo slide thiếu tiêu đề → lỗi `BANNER_TITLE_REQUIRED` |
| **AC-B02** | Tạo slide thiếu ảnh → lỗi `BANNER_IMAGE_REQUIRED` |
| **AC-B03** | Tạo slide chưa chọn loại/giá trị liên kết → lỗi `BANNER_TARGET_REQUIRED` |
| **AC-B04** | `targetValue` trỏ tới sản phẩm/danh mục/phiên không tồn tại → lỗi `INVALID_BANNER_TARGET` |
| **AC-B05** | `targetType = CATEGORY` + danh mục đang tắt → lỗi `INVALID_BANNER_TARGET` |
| **AC-B06** | Đã bật 5 slide, bật slide thứ 6 → lỗi `BANNER_ACTIVE_LIMIT_EXCEEDED` |
| **AC-B07** | Sửa slide đang bật (không đổi `active`) → **không** bị chặn |
| **AC-B08** | Không có loại liên kết `URL` trong enum; không có đường nhập URL tự do |
| **AC-B09** | `GET /client/home-banners` **không cần token**; trả theo `sortOrder`, chỉ slide `active` |
| **AC-B10** | Carousel tự chạy 6s; pause khi hover/focus; tôn trọng `prefers-reduced-motion` |
| **AC-B11** | Bấm slide điều hướng đúng route theo từng `targetType` |
| **AC-B12** | DB trống banner → trang chủ hiển thị hero mặc định, không vỡ layout |
| **AC-B13** | API banner lỗi → chỉ ẩn khối carousel; các khối khác (flash, danh mục, sản phẩm) vẫn chạy |
| **AC-B14** | Dark mode: scrim banner đủ tương phản, text overlay đọc được |
| **AC-B15** | Mobile: carousel kéo tay được, không tràn ngang |
| **AC-B16** | STAFF thấy menu "Banner trang chủ", tạo/sửa được, **không** có nút Xoá. CUSTOMER gọi API admin → 403 |

---

## 11. Truy vết

| BR | UC | AC | Code |
|---|---|---|---|
| BR-B01 | UC-B01 | AC-B02 | `HomeBannerService.applyImage` (`BANNER_IMAGE_REQUIRED`) |
| BR-B02 | UC-B01, UC-B03 | AC-B06, AC-B07 | `HomeBannerService.ensureActiveLimit` |
| BR-B03 | UC-B01 | AC-B08 | `BannerTargetType` (không có `URL`) + migration V7 |
| BR-B04 | UC-B01 | AC-B04, AC-B05 | `HomeBannerService.validateTarget` |
| BR-B05 | UC-B01 | AC-B04 | `validateTarget` case `PRODUCT` → `findByCodeIgnoreCase` |
| BR-B06 | UC-B01 | AC-B03 | `validateTarget` case `BRAND` → `findDistinctActiveFactories` |
| BR-B07 | UC-B01 | AC-B05 | `validateTarget` case `CATEGORY` → kiểm tra `isActive()` |
| — | UC-B04 | AC-B09 | `HomeBannerRepository.findByActiveTrueOrderBySortOrderAsc` |
| — | UC-B04 | AC-B10, AC-B11 | `home-banner-carousel` component |
| — | UC-B04 | AC-B12, AC-B13 | Home component (mỗi khối tải độc lập) |
