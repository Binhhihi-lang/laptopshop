# Tổng quan dự án — Laptopshop

> Bản cập nhật (v3) theo code hiện tại sau khi refactor **Coupon, Product, Category** sang chuẩn DTO + `AppException`/`ErrorCode`, đồng bộ hoàn toàn với module `User` (module mẫu ban đầu). Dự án vẫn đi theo hướng **Spring Boot REST API + HTML/Bootstrap/JS tĩnh**, không dùng JSP cho giao diện mới.

---

## 1. Kiến trúc tổng thể

```text
Frontend tĩnh (static/**)
HTML + Bootstrap + JS
        │ fetch() JSON
        ▼
Backend Spring Boot
@RestController → Service → Repository → JPA/Hibernate → MySQL
```

- **Frontend**: file tĩnh nằm trong `src/main/resources/static/`, Spring Boot tự serve theo đúng đường dẫn file thật.
- **Backend**: trả JSON qua các API dưới `/api/v1/**`, bọc trong `ApiResponse<T>` thống nhất (`result`, `message`).
- **Database**: các class trong `domain/` là bản thiết kế bảng MySQL thông qua JPA/Hibernate.
- **Ảnh upload động**: lưu ngoài project tại `D:/Spring/laptopshop/uploads/`, serve qua `/images-upload/**`.
- **Xử lý lỗi nghiệp vụ**: chuẩn hóa qua `AppException` + enum `ErrorCode` (mã số + message tiếng Việt + HttpStatus), áp dụng đồng bộ cho User, Category, Product, Coupon.

Cách hiểu dễ nhớ:

- `domain`: bản thiết kế bảng.
- `repository`: cửa vào database.
- `service`: nơi xử lý nghiệp vụ + validate + ném `AppException` khi sai.
- `dto/request`: lớp trung gian nhận dữ liệu thô từ Controller, Controller **không còn hứng trực tiếp bằng Entity** ở bất kỳ module CRUD chính nào nữa.
- `RestController`: cổng API nhận request (qua DTO) và trả JSON (qua `ApiResponse<T>`).
- HTML/JS tĩnh: giao diện gọi API bằng `fetch()`.

---

## 2. Cấu trúc hiện tại

```text
laptopshop/
├── pom.xml
├── src/main/java/com/example/laptopshop/
│   ├── config/
│   │   ├── SecurityConfiguration.java   ✅ PasswordEncoder
│   │   └── WebMvcConfig.java            ✅ permitAll tạm + /images-upload/**
│   ├── controller/
│   │   ├── ViewController.java           ✅ redirect /, /admin, /product/{id}
│   │   └── api/
│   │       ├── UserRestController.java     ✅ CRUD User (dùng DTO)
│   │       ├── RoleRestController.java     ✅ danh sách Role
│   │       ├── ProductRestController.java  ✅ CRUD Product (đã refactor sang DTO)
│   │       ├── CategoryRestController.java ✅ CRUD Category (đã refactor sang DTO)
│   │       └── CouponRestController.java   ✅ CRUD Coupon (đã refactor sang DTO)
│   ├── domain/
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── Product.java
│   │   ├── Category.java
│   │   ├── Order.java
│   │   ├── OrderDetail.java
│   │   ├── Coupon.java              ✅ đã có discountPercent VÀ discountAmount (2 kiểu giảm giá loại trừ nhau)
│   │   └── OrderStatus.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── User/UserCreationRequest.java, UserUpdateRequest.java
│   │   │   ├── Product/ProductCreationRequest.java, ProductUpdateRequest.java
│   │   │   ├── Category/CategoryCreationRequest.java, CategoryUpdateRequest.java
│   │   │   └── Coupon/CouponCreationRequest.java, CouponUpdateRequest.java
│   │   └── response/
│   │       └── ApiResponse.java     ✅ wrapper JSON thống nhất { result, message }
│   ├── exception/
│   │   ├── AppException.java        ✅ exception nghiệp vụ dùng chung
│   │   └── ErrorCode.java           ✅ enum mã lỗi tập trung, chia theo module (1xxx User, 2xxx Category, 3xxx Product, 4xxx Coupon, 5xxx Order/Cart, 9xxx System)
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── RoleRepository.java
│   │   ├── ProductRepository.java   ⚠️ cần bổ sung existsByCodeIgnoreCase(...), existsByCodeIgnoreCaseAndIdNot(...)
│   │   ├── CategoryRepository.java  ✅ existsByNameIgnoreCase(...)
│   │   └── CouponRepository.java    ⚠️ cần bổ sung existsByCodeIgnoreCase(...), existsByCodeIgnoreCaseAndIdNot(...)
│   └── service/
│       ├── UserService.java
│       ├── UploadService.java
│       ├── ProductService.java      ✅ đã refactor: nhận DTO, validate code trùng lặp qua existsByCodeIgnoreCaseAndIdNot
│       ├── CategoryService.java     ✅ đã refactor: nhận DTO, tự xử lý ảnh (trước đây nằm ở Controller)
│       └── CouponService.java       ✅ đã refactor: nhận DTO, hỗ trợ 2 kiểu giảm giá, có calculateDiscount() sẵn cho Order
└── src/main/resources/
    ├── application.properties
    └── static/
        ├── admin/dashboard/show.html
        ├── admin/layout/header.html, sidebar.html
        ├── admin/user/list.html, create.html, update.html, detail.html         ✅ Đầy đủ
        ├── admin/coupon/list.html, create.html, update.html, detail.html       ✅ Đầy đủ (hỗ trợ chọn % hoặc số tiền cố định)
        ├── admin/product/                                                      ❌ CHƯA CÓ — bước ưu tiên cao nhất tiếp theo
        ├── admin/category/                                                     ❌ CHƯA CÓ
        ├── client/home.html                 ⚠️ hiện chưa có nội dung đáng kể
        ├── css/admin-style.css, client-style.css
        └── js/scripts.js, admin-api.js (đã có CouponAPI, formatDiscount()), client-api.js ⚠️ còn trống/chưa đáng kể
```

---

## 3. Sơ đồ bảng / Entity Domain

Hiện tại `domain/` có 8 thành phần chính:

| Entity / Enum | Bảng DB        | Vai trò                               |
| ------------- | -------------- | -------------------------------------- |
| `Role`        | `roles`        | Vai trò tài khoản                     |
| `User`        | `users`        | Người dùng / quản trị viên            |
| `Category`    | `categories`   | Danh mục hoặc nhóm sản phẩm           |
| `Product`     | `products`     | Sản phẩm bán trong shop               |
| `Order`       | `orders`       | Đơn hàng                              |
| `OrderDetail` | `order_detail` | Chi tiết từng dòng sản phẩm trong đơn |
| `Coupon`      | `coupons`      | Mã giảm giá                           |
| `OrderStatus` | enum           | Trạng thái đơn hàng                   |

Quan hệ giữa các bảng:

```text
roles      1 ──── n users
users      1 ──── n orders
categories 1 ──── n products
orders     1 ──── n order_detail
products   1 ──── n order_detail
coupons    1 ──── n orders        (orders.coupon_id có thể null)
```

Giải thích ngắn:

- Một `Role` có nhiều `User`; mỗi `User` thuộc một `Role`.
- Một `User` có nhiều `Order`; mỗi `Order` thuộc một `User`.
- Một `Category` có nhiều `Product`; mỗi `Product` thuộc một `Category`.
- Một `Order` có nhiều `OrderDetail`; mỗi dòng chi tiết thuộc một đơn hàng.
- Một `Product` có thể xuất hiện trong nhiều `OrderDetail`.
- Một `Coupon` có thể dùng cho nhiều đơn hàng, nhưng đơn hàng có thể không dùng coupon.
- `OrderStatus` gồm `PENDING`, `CONFIRMED`, `SHIPPING`, `COMPLETED`, `CANCELLED`.

Các field đáng chú ý:

- `Product`: `code`, `name`, `price`, `image`, `detailDesc`, `shortDesc`, `quantity`, `sold`, `factory`, `target`, `active`, `createdAt`, `category`, `cpu`, `ram`, `storage`, `gpu`, `screen`, `os`, `weight`, `warrantyMonths`.
- `Order`: `orderCode`, `totalPrice`, `discountAmount`, `orderDate`, `status`, `coupon`, `user`, `orderDetails`.
- `Coupon`: `code`, `discountPercent` (Integer, nullable), `discountAmount` (Long, nullable), `expiryDate`, `usageLimit`, `usedCount`, `active`. **Chỉ được set đúng 1 trong 2 field `discountPercent`/`discountAmount`**, không được để trống cả 2 hay điền cả 2 — validate ở `CouponService.validateDiscountValue()`.

Điểm cần nhớ: `Order.java` hiện mới có field/annotation và TODO, chưa có getter/setter đầy đủ, chưa có repository/service/controller. `Coupon.java` đã hoàn thiện đầy đủ CRUD + DTO + validate, sẵn sàng để Order module gọi `couponService.calculateDiscount()` khi tính tiền đơn hàng.

---

## 4. Trạng thái từng module

| Module              | Backend                                        | Frontend                                  | Ghi chú                                                          |
| ------------------- | ----------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------ |
| **User Admin**      | ✅ Đầy đủ (chuẩn DTO)                            | ✅ Đầy đủ                                  | Module mẫu — pattern DTO gốc để nhân bản cho các module khác      |
| **Role**            | ✅ Đầy đủ                                        | —                                          | Phục vụ dropdown chọn vai trò                                    |
| **Dashboard Admin** | ✅ Cơ bản                                        | ✅ Cơ bản                                  | Product/Order còn cần số liệu thật                               |
| **Category Admin**  | ✅ Đầy đủ (đã refactor DTO, tự xử lý ảnh)         | ❌ Chưa có giao diện admin/category/**    | Backend xong hoàn toàn, chỉ còn thiếu HTML                       |
| **Product Admin**   | ✅ Đầy đủ (đã refactor DTO, validate code trùng)  | ❌ Chưa có giao diện admin/product/**     | **Ưu tiên cao nhất tiếp theo** — backend đã sẵn sàng chờ giao diện |
| **Coupon**          | ✅ Đầy đủ (2 kiểu giảm giá, DTO, ErrorCode chuẩn) | ✅ Đầy đủ (list/create/update/detail)      | Module hoàn chỉnh thứ 2 sau User                                  |
| **Order Admin**     | ⚠️ Mới có entity                                 | ❌ Chưa có                                 | Chưa có repository/service/controller — module lớn tiếp theo      |
| **OrderDetail**     | ⚠️ Mới có entity                                 | —                                          | Phục vụ chi tiết đơn hàng, cần field snapshot đã có sẵn            |
| **Client Home**     | ⚠️ Có thể dùng Product API                       | ⚠️ `home.html` còn trống/chưa đáng kể      | Cần dựng giao diện và gọi API                                     |
| **Client API JS**   | —                                                | ⚠️ `client-api.js` còn trống/chưa đáng kể  | Cần thêm hàm gọi API phía client                                  |
| **Cart**            | ❌ Chưa có                                       | ❌ Chưa có                                 | Cần thiết kế riêng, thường đi kèm session hoặc bảng riêng          |
| **Auth/Security**   | ⚠️ `permitAll()` tạm thời, chưa có JWT           | —                                          | Rủi ro lớn nhất về mặt "sẵn sàng phỏng vấn" — xem mục 10           |

---

## 5. Luồng học dự án nên đi theo

Nên học theo thứ tự:

1. **Domain**: hiểu bảng nào tồn tại và chúng liên hệ với nhau thế nào.
2. **Repository**: hiểu Spring Data JPA giúp đọc/ghi database ra sao.
3. **Service**: hiểu nơi gom xử lý nghiệp vụ + validate + ném `AppException`.
4. **DTO**: hiểu vì sao Controller không nên hứng trực tiếp Entity (bảo mật, tách biệt hợp đồng API với cấu trúc DB, kiểm soát field nào client được set).
5. **RestController**: hiểu URL API nào nhận request (qua DTO) và trả JSON (qua `ApiResponse<T>`).
6. **Frontend tĩnh**: hiểu HTML/JS gọi API bằng `fetch()` như thế nào.

Thứ tự học/module mẫu tham khảo theo độ hoàn thiện hiện tại: **User → Coupon → Product (backend) → Category (backend)**. Cả 4 module này giờ dùng chung một pattern DTO nhất quán, đọc 1 module là hiểu được cả 4.

---

## 6. Các quyết định kiến trúc quan trọng

1. **Dùng HTML tĩnh thay JSP**: giao diện đặt trong `src/main/resources/static/`, không cần controller trả view.
2. **REST API trả JSON qua `ApiResponse<T>`**: mọi response thành công đều bọc trong `{ result, message }`, lỗi bọc qua `AppException` → `ErrorCode`.
3. **Controller không hứng trực tiếp Entity**: mọi module CRUD chính (User, Product, Category, Coupon) đều nhận dữ liệu qua DTO riêng (`XxxCreationRequest`/`XxxUpdateRequest`), tách biệt hợp đồng API khỏi cấu trúc bảng DB. Các field hệ thống tự quản lý (`usedCount`, `sold`, `createdAt`...) không xuất hiện trong DTO tạo mới để tránh client tự set sai.
4. **Chuẩn hóa lỗi nghiệp vụ qua `ErrorCode`**: mỗi module có 1 dải mã riêng (1xxx User, 2xxx Category, 3xxx Product, 4xxx Coupon, 5xxx Order/Cart, 9xxx System), tránh dùng `IllegalArgumentException`/`NoSuchElementException` rải rác như giai đoạn đầu.
5. **Validate trùng lặp dùng chung 1 hàm `validateCode(code, currentId)`**: `currentId == null` là tạo mới, khác `null` là update (loại trừ chính bản ghi đó khỏi kiểm tra trùng) — áp dụng cho Product và Coupon, tránh viết trùng logic ở 2 chỗ create/update.
6. **Không tự viết resource handler cho CSS/JS/ảnh tĩnh**: Spring Boot tự serve nội dung trong `static/`.
7. **Ảnh upload nằm ngoài `static/`**: dùng `upload.directory` và map qua `/images-upload/**`.
8. **URL tĩnh phải đúng đường dẫn file thật**: ví dụ `/admin/user/list.html`, không phải `/admin/user`.
9. **Layout dùng file HTML nhỏ rồi fetch vào trang chính**: admin có `header.html` và `sidebar.html`, nhúng bằng `initAdminLayout()`.
10. **Mỗi vùng có API JS riêng**: admin dùng `admin-api.js` (đã có UserAPI, CategoryAPI, ProductAPI, CouponAPI), client sẽ dùng `client-api.js`.
11. **Upload file dùng `multipart/form-data`**: JS dùng `FormData`, Spring dùng `@ModelAttribute` và `MultipartFile` ngay trong DTO (không tách `@RequestPart` JSON + `@RequestParam` file riêng nữa).
12. **Coupon là JSON thuần `@RequestBody`**: vì không có ảnh, khác với User/Product/Category dùng form-data.
13. **Security đang mở tạm thời**: `permitAll()` để dev nhanh, sau này khóa `/admin/**` theo quyền admin — đây là việc ưu tiên cao trước khi đưa dự án vào CV.

---

## 7. Các điểm lệch cần nhớ để sửa sau

1. **Upload folder đang lệch chữ hoa/thường**

   Backend đang gọi `UploadService` với folder `Avatar`/`Product`, trong khi helper frontend tạo URL `/images-upload/avatar/...` và `/images-upload/product/...`. Trên Windows thường vẫn chạy vì không phân biệt hoa/thường, nhưng nên thống nhất để tránh lỗi khi chuyển môi trường (đặc biệt khi deploy lên Linux server).

2. **Client còn rất sơ khai**

   `client/home.html` và `client-api.js` hiện chưa có nội dung đáng kể, nên phần client shop cần được thiết kế sau khi hoàn thiện giao diện Admin Product/Category.

3. **JSP dependency vẫn còn trong `pom.xml`**

   `tomcat-embed-jasper` và JSTL vẫn còn. Vì dự án đã chuyển hẳn sang HTML tĩnh, có thể dọn sau khi chắc chắn không còn JSP.

4. **Repository của Product và Coupon còn thiếu method mới**

   `ProductRepository` cần `existsByCodeIgnoreCase(String)` và `existsByCodeIgnoreCaseAndIdNot(String, Long)`. `CouponRepository` cần tương tự cho `code`. Cả 2 Service đã viết sẵn logic gọi các method này, chỉ còn thiếu khai báo interface.

5. **DTO package của User đang lệch namespace so với import thực tế**

   `UserRestController` import `dto.request.User.UserCreationRequest`, nhưng `UserService` lại import `dto.request.UserCreationRequest` (thiếu subfolder `User`). Cần rà soát và thống nhất về 1 package duy nhất — các DTO mới (Product/Category/Coupon) đều đã theo convention có subfolder (`dto.request.Product.*`, v.v.) nên chỉ cần sửa lại đúng 2 dòng import ở `UserService`.

6. **Chưa có `@ExceptionHandler` toàn cục cho `AppException`**

   Hiện `ErrorCode` đã có `HttpStatus` gắn sẵn cho từng mã lỗi, nhưng cần một `@RestControllerAdvice` bắt `AppException` và map ra `ApiResponse` + đúng HTTP status — nếu chưa có, tất cả lỗi nghiệp vụ hiện tại có thể đang trả về 500 thay vì đúng mã (400/404/...).

---

## 8. Cách chạy dự án

### Yêu cầu

- Java 17.
- Maven hoặc `mvnw` có sẵn.
- MySQL chạy ở `localhost:3306`.
- Database tên `laptopshop`.

### Kiểm tra cấu hình

```properties
spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:3306/laptopshop
spring.datasource.username=root
spring.datasource.password=123456
spring.jpa.hibernate.ddl-auto=update
upload.directory=D:/Spring/laptopshop/uploads/
```

### Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

Hoặc chạy trực tiếp `LaptopshopApplication.java` từ IDE.

### URL thường dùng

- Trang chủ: `http://localhost:8080/`
- Dashboard admin: `http://localhost:8080/admin`
- Quản lý người dùng: `http://localhost:8080/admin/user/list.html`
- Quản lý mã giảm giá: `http://localhost:8080/admin/coupon/list.html`
- API users: `http://localhost:8080/api/v1/users`
- API roles: `http://localhost:8080/api/v1/roles`
- API products: `http://localhost:8080/api/v1/products`
- API categories: `http://localhost:8080/api/v1/categories`
- API coupons: `http://localhost:8080/api/v1/coupons`

---

## 9. Những thay đổi đã triển khai gần đây

### Giai đoạn Domain (trước đó)

Đã bổ sung field cấu hình laptop cho `Product` (`cpu`, `ram`, `storage`, `gpu`, `screen`, `os`, `weight`, `warrantyMonths`), field `slug`/`description` cho `Category`, và field snapshot (`productCode`, `productName`, `productImage`) cho `OrderDetail`.

### Giai đoạn chuẩn hóa DTO + ErrorCode (mới nhất)

1. **Coupon — hoàn thiện toàn bộ CRUD**
   - `Coupon` domain hỗ trợ 2 kiểu giảm giá loại trừ nhau: `discountPercent` hoặc `discountAmount`.
   - `CouponService` validate đúng 1 trong 2 kiểu, gộp logic kiểm tra trùng code qua `validateCode(code, currentId)`.
   - Thêm `CouponService.calculateDiscount(coupon, orderTotal)` — sẵn sàng để module Order gọi khi tính tiền đơn hàng.
   - Chuyển từ `NoSuchElementException`/`IllegalArgumentException` sang `AppException` + `ErrorCode` (`COUPON_NOT_FOUND`, `COUPON_ALREADY_EXISTS`, `COUPON_CODE_REQUIRED`, `INVALID_COUPON_CONFIG`, `INVALID_DISCOUNT_PERCENT`, `INVALID_DISCOUNT_AMOUNT`).
   - Thêm `CouponCreationRequest`/`CouponUpdateRequest`, Controller không còn hứng `@RequestBody Coupon` trực tiếp.
   - Giao diện admin `list/create/update/detail.html` cho phép chọn hình thức giảm giá qua radio button.

2. **Product — refactor sang DTO**
   - Thêm `ProductCreationRequest`/`ProductUpdateRequest`, gộp toàn bộ field text + `categoryId` (Long) + `inputFile` vào DTO thay vì `@RequestPart Product` + `@RequestParam MultipartFile` tách rời.
   - `ProductService` đảm nhiệm toàn bộ validate (`code`, `name`, `price`, `categoryId`) + lookup `Category` + upload/xóa ảnh, dùng `ErrorCode.PRODUCT_CODE_REQUIRED`/`PRODUCT_ALREADY_EXISTS`/`PRODUCT_NAME_EMPTY`/`PRODUCT_PRICE_INVALID`/`PRODUCT_CATEGORY_REQUIRED`.

3. **Category — refactor sang DTO**
   - Thêm `CategoryCreationRequest`/`CategoryUpdateRequest`.
   - `CategoryService` nhận thêm `UploadService`, tự xử lý lưu/xóa ảnh (trước đây nằm ở Controller).

### Xác nhận kỹ thuật

- Các thay đổi trên là refactor tầng Service/Controller/DTO, **chưa build lại project sau cùng** — cần bạn tự chạy `mvnw.cmd -q -DskipTests compile` sau khi bổ sung các method Repository còn thiếu (mục 7.4) và các mã `ErrorCode` mới nếu có.

---

## 10. Roadmap ưu tiên — góc nhìn Senior Backend, hướng tới đi thực tập/xin việc

Dưới đây là lộ trình được sắp theo mức độ **"nhà tuyển dụng Java Backend Intern/Fresher sẽ hỏi gì khi nhìn CV/project này"**, không chỉ theo độ khó kỹ thuật. Mục tiêu: từ một project CRUD tốt trở thành một project thể hiện được tư duy backend đúng chuẩn.

### Phase 1 — Vá các lỗ hổng nền tảng (làm trước khi làm thêm tính năng mới)

Đây là những thứ nhà tuyển dụng/interviewer nhìn vào code base sẽ hỏi ngay, vì thiếu chúng khiến project trông như "chưa từng chạy thật":

1. **`@RestControllerAdvice` xử lý exception toàn cục**
   - Bắt `AppException` → trả `ApiResponse` với đúng `HttpStatus` từ `ErrorCode`.
   - Bắt `MethodArgumentNotValidException` (khi bật `@Valid`) → trả lỗi field-level rõ ràng.
   - Bắt `Exception` chung → `ErrorCode.UNCATEGORIZED_EXCEPTION`, không để lộ stack trace ra JSON.
   - Đây gần như là câu hỏi **chắc chắn gặp** khi phỏng vấn Spring Boot: "Bạn xử lý exception tập trung thế nào?"

2. **Bật `@Valid` thật sự trên Controller**
   - Hiện các DTO đã có `@NotBlank`/`@NotNull` nhưng Controller chưa gắn `@Valid`, nghĩa là annotation đang "trang trí", validate thật vẫn nằm thủ công trong Service. Gắn `@Valid` + để `GlobalExceptionHandler` xử lý là cách làm chuẩn, giảm code lặp trong Service.

3. **Spring Security thật (JWT) thay vì `permitAll()`**
   - Đây là điểm **quan trọng nhất** để một project CRUD trở thành project "có backend thật sự": login trả JWT, filter xác thực token, phân quyền `ROLE_ADMIN` cho `/api/v1/**` (trừ endpoint public như xem sản phẩm), `ROLE_USER` cho phần client/đặt hàng.
   - Đây cũng là chủ đề interview phổ biến nhất cho vị trí Java Backend Intern ở Việt Nam hiện nay.

4. **Chuẩn hóa Repository còn thiếu** (mục 7.4) và **dọn lệch package DTO của User** (mục 7.5) — việc nhỏ nhưng thể hiện sự cẩn thận khi review code.

### Phase 2 — Hoàn thiện luồng nghiệp vụ lõi (Order/Cart)

Đây là phần **quan trọng nhất về mặt business logic** — một shop bán hàng mà chưa có luồng đặt hàng thì portfolio chưa "kể được câu chuyện" trọn vẹn:

1. **Cart**: có thể làm đơn giản trước — lưu theo `User` (bảng `cart_item` hoặc field JSON), sau này nâng cấp lên Redis nếu muốn thể hiện thêm kỹ năng.
2. **Order + OrderDetail**: `OrderService.createOrder()` cần xử lý transaction đúng cách (`@Transactional`) — trừ tồn kho (`Product.quantity`), tăng `Product.sold`, áp dụng `couponService.calculateDiscount()` đã viết sẵn, tăng `Coupon.usedCount`, snapshot dữ liệu sản phẩm vào `OrderDetail`.
3. Đây là chỗ tốt nhất để thể hiện hiểu biết về **transaction, tính toàn vẹn dữ liệu, race condition khi nhiều người cùng mua 1 sản phẩm gần hết hàng** — chủ đề senior rất thích hỏi để phân biệt ứng viên "biết CRUD" và ứng viên "hiểu backend".

### Phase 3 — Chất lượng code & khả năng vận hành

1. **Unit test + Integration test** (JUnit 5, Mockito, `@SpringBootTest` với H2 hoặc Testcontainers) cho tối thiểu `CouponService` (nhiều nhánh validate) và `OrderService` (nhiều logic transaction). Có test là điểm cộng rất lớn cho fresher — đa số project sinh viên không có.
2. **Pagination + filter + sort** cho danh sách Product/Order (`Pageable`, `Specification` hoặc `@Query` động) — tránh trả `findAll()` không giới hạn, một lỗi rất phổ biến bị hỏi trong interview.
3. **Chuyển `ddl-auto=update` sang Flyway/Liquibase migration** — thể hiện hiểu biết về quản lý schema production, tránh rủi ro mất dữ liệu khi đổi field.
4. **Swagger/OpenAPI** (`springdoc-openapi`) — giúp demo API trực quan khi phỏng vấn, cũng là công cụ thực tế hầu hết công ty dùng.
5. **Logging có cấu trúc** (SLF4J + Logback, tách log theo môi trường) thay vì chỉ dựa vào `console.error` phía frontend.

### Phase 4 — Điểm cộng để nổi bật (làm nếu còn thời gian)

1. **Tích hợp thanh toán thật** (VNPay/Momo sandbox) cho web — bạn đã có kinh nghiệm ZaloPay bên app Android "Huce Travel", việc mang kinh nghiệm đó sang project laptopshop tạo câu chuyện nhất quán, dễ kể trong phỏng vấn.
2. **Dockerize**: `Dockerfile` cho app + `docker-compose.yml` (app + MySQL) — giúp người phỏng vấn chạy thử project trong 1 lệnh, ấn tượng tốt.
3. **CI cơ bản** (GitHub Actions chạy `mvn test` mỗi lần push) — không cần CD phức tạp, chỉ cần thể hiện quen với quy trình.
4. **Redis cache** cho danh sách sản phẩm/category (dữ liệu đọc nhiều, ghi ít) — nếu muốn thể hiện thêm về hiệu năng.

### Gợi ý thứ tự làm cụ thể (rút gọn thành checklist)

1. ✅ ~~User/Product/Category/Coupon CRUD chuẩn DTO~~ (đã xong)
2. ⬜ `GlobalExceptionHandler` + bật `@Valid`
3. ⬜ Giao diện admin Product + Category (đang có backend, thiếu HTML)
4. ⬜ Spring Security + JWT + phân quyền
5. ⬜ Cart + Order + OrderDetail (kèm `@Transactional`)
6. ⬜ Pagination cho Product/Order
7. ⬜ Unit test cho Service quan trọng
8. ⬜ Swagger + Dockerize
9. ⬜ (Tùy thời gian) Thanh toán thật, Redis cache, CI

Nếu mục tiêu gần nhất là buổi phỏng vấn thực tập, ưu tiên **1 → 4 → 5** trước — đó là bộ 3 chủ đề gần như chắc chắn được hỏi (xử lý lỗi, bảo mật, transaction), sau đó mới quay lại làm đẹp giao diện Product/Category.

---

## 11. Ghi chú xác nhận

- Bản v3 này chỉ cập nhật tài liệu tổng quan theo code mới nhất (Coupon/Product/Category đã refactor DTO); phần "Roadmap" ở mục 10 là định hướng đề xuất, chưa phải code đã triển khai.
- Cần tự chạy `mvnw.cmd -q -DskipTests compile` sau khi bổ sung Repository/ErrorCode còn thiếu được liệt kê ở mục 7.
- Các thay đổi Domain/Service/Controller/DTO ở các phiên làm việc gần đây được xem là baseline hiện tại, giữ nguyên khi code tiếp.
