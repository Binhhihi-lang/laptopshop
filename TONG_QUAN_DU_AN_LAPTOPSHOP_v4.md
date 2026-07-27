# Tổng quan dự án — Laptopshop

> Bản cập nhật (v4) — tính từ v3: đã hoàn thiện **MapStruct + Response DTO** cho cả 4 module (User/Category/Product/Coupon), **Spring Security + JWT** (đăng nhập, phân quyền theo Role, xử lý lỗi 401/403 chuẩn), **User có nhiều Role** (`@ManyToMany`) kèm **xóa mềm** qua `@SQLDelete`/`@Where`, và seed tài khoản admin mặc định lúc khởi động. Dự án vẫn đi theo hướng **Spring Boot REST API + HTML/Bootstrap/JS tĩnh**, không dùng JSP.

---

## 1. Kiến trúc tổng thể

```text
Frontend tĩnh (static/**)
HTML + Bootstrap + JS
        │ fetch() JSON + Authorization: Bearer <JWT>
        ▼
Spring Security (xác thực JWT, phân quyền theo Role)
        │
        ▼
@RestController → Service (@Transactional) → Mapper (MapStruct) → Repository → JPA/Hibernate → MySQL
```

- **Frontend**: file tĩnh trong `src/main/resources/static/`, gọi API bằng `fetch()`, tự gắn `Authorization: Bearer <token>` (token lưu trong `localStorage` sau khi đăng nhập).
- **Backend**: trả JSON qua `/api/v1/**`, bọc trong `ApiResponse<T>` thống nhất (`code`, `message`, `result`).
- **Bảo mật**: mọi request (trừ `/api/v1/auth/**` và GET sản phẩm/danh mục) bắt buộc JWT hợp lệ; phân quyền theo Role qua `hasRole(...)`.
- **Chuyển đổi dữ liệu**: MapStruct đảm nhiệm cả 2 chiều — DTO → Entity (field thuần túy) và Entity → Response DTO — luôn chạy **bên trong `@Transactional` của Service**, không phải ở Controller, để tránh `LazyInitializationException` với các quan hệ `@ManyToOne`/`@ManyToMany`.
- **Xóa mềm**: áp dụng cho `User` (nhiều Role) qua `@SQLDelete` + `@Where` ở tầng Entity — Service/Controller gọi `delete()` y hệt như xóa thật, Hibernate tự đổi thành `UPDATE deleted_at = NOW()`.
- **Ảnh upload động**: lưu ngoài project, serve qua `/images-upload/**` (đã đưa CSDL lên aiven.io và ảnh lên Cloudinary).
- **Xử lý lỗi nghiệp vụ**: `AppException` + enum `ErrorCode` (mã số + message tiếng Việt + HttpStatus) cho toàn bộ 4 module CRUD, cộng thêm xử lý riêng cho lỗi xác thực/phân quyền (401/403) ngay trong `SecurityConfiguration` vì 2 loại lỗi này xảy ra trước khi request chạm tới `DispatcherServlet`, `@RestControllerAdvice` thông thường không bắt được.

Cách hiểu dễ nhớ (đã thêm tầng `mapper`):

- `domain`: bản thiết kế bảng.
- `repository`: cửa vào database.
- `service`: nơi xử lý nghiệp vụ + validate + ném `AppException`, đồng thời là nơi **duy nhất** gọi `mapper` để convert Entity ↔ DTO (trong `@Transactional`).
- `mapper`: interface MapStruct, sinh code convert tại compile-time, không dùng reflection.
- `dto/request`: nhận dữ liệu thô từ Controller.
- `dto/response`: hình dạng dữ liệu trả ra ngoài, **không** phải Entity — tránh lộ field nhạy cảm (password) và tránh vòng lặp tham chiếu (`Product` không chứa lại `Category`, và ngược lại).
- `RestController`: chỉ gọi Service rồi bọc kết quả (đã là DTO) vào `ApiResponse<T>` — không tự gọi mapper nữa.
- `config`: `SecurityConfiguration`, `JwtAuthenticationEntryPoint` (401), `JwtAccessDeniedHandler` (403), `ApplicationInitConfig` (seed admin).

---

## 2. Cấu trúc hiện tại

```text
laptopshop/
├── pom.xml                                  ⚠️ cần có mapstruct, mapstruct-processor,
│                                                spring-boot-starter-oauth2-resource-server, nimbus-jose-jwt
├── src/main/java/com/example/laptopshop/
│   ├── config/
│   │   ├── SecurityConfiguration.java        ✅ JWT resource server + phân quyền theo path/Role
│   │   ├── JwtAuthenticationEntryPoint.java  ✅ xử lý 401 (thiếu/sai/hết hạn token)
│   │   ├── JwtAccessDeniedHandler.java       ✅ xử lý 403 (đủ token nhưng sai quyền)
│   │   ├── ApplicationInitConfig.java        ✅ seed tài khoản admin mặc định nếu DB user rỗng
│   │   └── WebMvcConfig.java                 ✅ /images-upload/**
│   ├── controller/
│   │   ├── ViewController.java
│   │   └── api/
│   │       ├── AuthenticationController.java  ✅ POST /auth/login, /auth/introspect
│   │       ├── UserRestController.java        ✅ CRUD User, trả UserResponse (roleNames: List<String>)
│   │       ├── RoleRestController.java        ✅ danh sách Role
│   │       ├── ProductRestController.java     ✅ CRUD Product, trả ProductResponse
│   │       ├── CategoryRestController.java    ✅ CRUD Category, GET /{id} trả CategoryDetailResponse (kèm products)
│   │       └── CouponRestController.java      ✅ CRUD Coupon, trả CouponResponse
│   ├── domain/
│   │   ├── User.java                 ✅ roles: Set<Role> (@ManyToMany), deletedAt, @SQLDelete/@Where (xóa mềm)
│   │   ├── Role.java
│   │   ├── Product.java
│   │   ├── Category.java             (quan hệ 1-n tới Product qua mappedBy)
│   │   ├── Order.java                ⚠️ mới có entity
│   │   ├── OrderDetail.java          ⚠️ mới có entity
│   │   ├── Coupon.java               ✅ discountPercent VÀ discountAmount (2 kiểu giảm giá loại trừ nhau)
│   │   └── OrderStatus.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── Auth/AuthenticationRequest.java, IntrospectRequest.java
│   │   │   ├── User/UserCreationRequest.java, UserUpdateRequest.java   (roleNames: List<String>)
│   │   │   ├── Product/ProductCreationRequest.java, ProductUpdateRequest.java
│   │   │   ├── Category/CategoryCreationRequest.java, CategoryUpdateRequest.java
│   │   │   └── Coupon/CouponCreationRequest.java, CouponUpdateRequest.java
│   │   └── response/
│   │       ├── ApiResponse.java                          ✅ wrapper JSON { code, message, result }
│   │       ├── AuthenticationResponse.java, IntrospectResponse.java
│   │       ├── User/UserResponse.java                    (roleNames: List<String>, KHÔNG có password)
│   │       ├── Product/ProductResponse.java               (categoryId/categoryName phẳng, không lồng Category)
│   │       ├── Category/CategoryResponse.java              (dùng cho list, KHÔNG có products)
│   │       ├── Category/CategoryDetailResponse.java        (dùng cho detail, CÓ List<ProductResponse>)
│   │       └── Coupon/CouponResponse.java
│   ├── mapper/
│   │   ├── UserMapper.java       ✅ roles (Set<Role>) → roleNames (List<String>) qua helper method
│   │   ├── ProductMapper.java    ✅ category → categoryId/categoryName
│   │   ├── CategoryMapper.java   ✅ uses = ProductMapper.class cho toDetailResponse()
│   │   └── CouponMapper.java
│   ├── exception/
│   │   ├── AppException.java
│   │   └── ErrorCode.java        ✅ đã thêm ROLE_NOT_FOUND (1010), USER_ROLES_EMPTY (1011)
│   ├── repository/
│   │   ├── UserRepository.java, RoleRepository.java
│   │   ├── ProductRepository.java, CategoryRepository.java, CouponRepository.java
│   └── service/
│       ├── AuthenticationService.java  ✅ đăng nhập (BCrypt so khớp), phát hành JWT (HS512), introspect
│       ├── UserService.java            ✅ @Transactional, getRolesByNames(), trả *Response trực tiếp
│       ├── UploadService.java
│       ├── ProductService.java         ✅ @Transactional, mapping trong Service (không phải Controller)
│       ├── CategoryService.java        ✅ @Transactional, getCategoryDetail() nạp kèm products an toàn
│       └── CouponService.java
└── src/main/resources/
    ├── application.properties
    └── static/
        ├── admin/login.html                                                   ✅ đăng nhập, lưu token vào localStorage
        ├── admin/dashboard/show.html
        ├── admin/layout/header.html, sidebar.html
        ├── admin/user/list.html, create.html, update.html, detail.html        ✅ hỗ trợ chọn NHIỀU Role (checkbox)
        ├── admin/coupon/list.html, create.html, update.html, detail.html      ✅ Đầy đủ, gửi FormData (đồng bộ User/Product/Category)
        ├── admin/product/, admin/category/                                    Backend xong, giao diện đang tự hoàn thiện
        ├── client/home.html                 ⚠️ chưa có nội dung đáng kể
        ├── css/admin-style.css, client-style.css
        └── js/scripts.js, admin-guard.js, admin-api.js (tự gắn JWT + unwrap ApiResponse.result), client-api.js ⚠️ còn trống
```

---

## 3. Sơ đồ bảng / Entity Domain

| Entity / Enum | Bảng DB        | Vai trò                                                |
| ------------- | -------------- | ------------------------------------------------------ |
| `Role`        | `roles`        | Vai trò tài khoản                                      |
| `User`        | `users`        | Người dùng / quản trị viên — có xóa mềm (`deleted_at`) |
| `Category`    | `categories`   | Danh mục hoặc nhóm sản phẩm                            |
| `Product`     | `products`     | Sản phẩm bán trong shop                                |
| `Order`       | `orders`       | Đơn hàng                                               |
| `OrderDetail` | `order_detail` | Chi tiết từng dòng sản phẩm trong đơn                  |
| `Coupon`      | `coupons`      | Mã giảm giá                                            |
| `OrderStatus` | enum           | Trạng thái đơn hàng                                    |

Quan hệ giữa các bảng (đã cập nhật User-Role thành nhiều-nhiều):

```text
users      n ──── n roles          (bảng trung gian user_roles)
users      1 ──── n orders
categories 1 ──── n products
orders     1 ──── n order_detail
products   1 ──── n order_detail
coupons    1 ──── n orders          (orders.coupon_id có thể null)
```

Điểm cần nhớ:

- `User` giờ có `Set<Role> roles` (trước là 1 Role) — 1 user có thể vừa là `ADMIN` vừa có role khác cùng lúc. JWT claim `scope` chứa nhiều tên role cách nhau bởi khoảng trắng (vd `"ADMIN STAFF"`), `JwtGrantedAuthoritiesConverter` mặc định của Spring Security tự tách thành nhiều quyền `ROLE_ADMIN`, `ROLE_STAFF`.
- `User` xóa mềm qua field `deletedAt` (null = chưa xóa) + `@SQLDelete`/`@Where` ở Entity — **cột `email` không còn `unique = true` ở DB**, chỉ chống trùng ở tầng ứng dụng (`UserService.validateEmail`) vì `@Where` khiến user đã xóa mềm "biến mất" khỏi check trùng, nhưng ràng buộc UNIQUE ở DB thì không phân biệt được điều đó.
- `Coupon`: `code`, `discountPercent` (Integer, nullable), `discountAmount` (Long, nullable) — chỉ được set đúng 1 trong 2, validate ở `CouponService.validateDiscountValue()`.
- `Order.java`/`OrderDetail.java` mới có entity, chưa có repository/service/controller — module lớn tiếp theo.

---

## 4. Trạng thái từng module

| Module              | Backend                                                                   | Frontend                             | Ghi chú                                                        |
| ------------------- | ------------------------------------------------------------------------- | ------------------------------------ | -------------------------------------------------------------- |
| **Auth (JWT)**      | ✅ Đầy đủ (login, introspect, 401/403 chuẩn hóa)                          | ✅ `login.html`                      | Token lưu `localStorage`, tự đăng xuất khi 401                 |
| **User Admin**      | ✅ Đầy đủ (DTO + MapStruct + Transactional + nhiều Role + xóa mềm)        | ✅ Đầy đủ (checkbox chọn nhiều Role) | Module tham chiếu đầy đủ nhất, áp dụng mọi pattern mới nhất    |
| **Role**            | ✅ Đầy đủ                                                                 | —                                    | Phục vụ checkbox chọn nhiều Role                               |
| **Dashboard Admin** | ✅ Cơ bản                                                                 | ✅ Có                                | Product/Order còn cần số liệu thật                             |
| **Category Admin**  | ✅ Đầy đủ (DTO + MapStruct + Transactional, detail kèm danh sách Product) | Đang hoàn thiện                      | Backend đã áp dụng pattern mới nhất (`CategoryDetailResponse`) |
| **Product Admin**   | ✅ Đầy đủ (DTO + MapStruct + Transactional)                               | Đang hoàn thiện                      | Backend đã áp dụng pattern mới nhất                            |
| **Coupon**          | ✅ Đầy đủ (2 kiểu giảm giá, DTO, MapStruct, ErrorCode chuẩn)              | ✅ Đầy đủ                            | Đã đổi sang gửi FormData đồng bộ với User/Product/Category     |
| **Order Admin**     | ⚠️ Mới có entity                                                          | ❌ Chưa có                           | Module lớn tiếp theo — xem roadmap                             |
| **OrderDetail**     | ⚠️ Mới có entity                                                          | —                                    | Field snapshot đã có sẵn                                       |
| **Client Home**     | ⚠️ Có thể dùng Product API                                                | ⚠️ `home.html` còn trống             | Cần dựng giao diện và gọi API                                  |
| **Client API JS**   | —                                                                         | ⚠️ `client-api.js` còn trống         | Cần thêm hàm gọi API phía client                               |
| **Cart**            | ❌ Chưa có                                                                | ❌ Chưa có                           | Cần thiết kế riêng                                             |
| **Auth/Security**   | ✅ JWT + phân quyền theo Role + xử lý 401/403                             | ✅ `admin-guard.js`, `login.html`    | Rủi ro lớn nhất trước đây — **đã giải quyết**                  |

---

## 5. Luồng học dự án nên đi theo

1. **Domain**: bảng nào tồn tại, liên hệ ra sao (đặc biệt chú ý User-Role giờ là nhiều-nhiều).
2. **Repository**: Spring Data JPA đọc/ghi database.
3. **Service**: nơi xử lý nghiệp vụ + validate + ném `AppException` + **gọi Mapper trong `@Transactional`**.
4. **Mapper (MapStruct)**: vì sao tách 2 chiều rõ ràng (Request→Entity giữ tay field có logic đặc biệt; Entity→Response để tránh lộ dữ liệu/vòng lặp).
5. **DTO**: Controller không hứng trực tiếp Entity, không trả trực tiếp Entity.
6. **RestController**: giờ rất mỏng — chỉ gọi Service, bọc `ApiResponse<T>`.
7. **Security**: JWT được verify ở tầng filter (`SecurityConfiguration`) trước khi tới Controller; phân quyền theo path + Role.
8. **Frontend tĩnh**: `admin-api.js` tự gắn token, tự unwrap `ApiResponse.result`.

Module tham khảo đầy đủ nhất hiện tại: **User** (áp dụng tất cả pattern mới nhất: DTO, MapStruct, Transactional, nhiều Role, xóa mềm, JWT).

---

## 6. Các quyết định kiến trúc quan trọng

1. **HTML tĩnh thay JSP**: `src/main/resources/static/`, không cần controller trả view.
2. **REST API trả JSON qua `ApiResponse<T>`**: `{ code, message, result }`.
3. **Controller không hứng/trả trực tiếp Entity**: nhận qua `XxxCreationRequest`/`XxxUpdateRequest`, trả qua `XxxResponse` — 2 chiều đều qua DTO.
4. **MapStruct đứng trong tầng `@Transactional` của Service, không phải Controller**: đây là quyết định quan trọng nhất mới bổ sung. Field nào cần logic đặc biệt (trim, uppercase, normalize null, hash password, lookup quan hệ) thì `@Mapping(target = "...", ignore = true)` và set tay; field thuần túy để MapStruct tự copy. Entity→Response luôn map **trước khi** transaction đóng, để truy cập quan hệ lazy (`@ManyToOne`, `@ManyToMany`, `@OneToMany`) không bị `LazyInitializationException` bất kể cấu hình `spring.jpa.open-in-view`.
5. **2 Response DTO khác nhau cho cùng 1 Entity khi cần**: `CategoryResponse` (danh sách, không có `products`) và `CategoryDetailResponse` (chi tiết, có `List<ProductResponse>`) — tránh nạp thừa dữ liệu ở trang danh sách, đồng thời `ProductResponse` không chứa lại `Category` để triệt tiêu vòng lặp tham chiếu.
6. **Xóa mềm qua `@SQLDelete` + `@Where` ở tầng Entity**: không cần sửa Service/Controller, `delete()` gọi y hệt như xóa thật. Đánh đổi: phải bỏ `unique = true` ở cột có khả năng bị ảnh hưởng (vd `email`), dồn hết việc chống trùng vào tầng ứng dụng.
7. **Chuẩn hóa lỗi nghiệp vụ qua `ErrorCode`**: mỗi module 1 dải mã riêng (1xxx User, 2xxx Category, 3xxx Product, 4xxx Coupon, 5xxx Order/Cart, 9xxx System).
8. **Lỗi 401/403 xử lý riêng trong `SecurityConfiguration`, không phải `@RestControllerAdvice`**: `AuthenticationException`/`AccessDeniedException` bị `ExceptionTranslationFilter` bắt trước khi request chạm `DispatcherServlet`, nên phải dùng `AuthenticationEntryPoint` (401) và `AccessDeniedHandler` (403) đăng ký thẳng trong `SecurityFilterChain`.
9. **JWT đối xứng HS512**: `AuthenticationService` ký token bằng `jwt.signerKey` (đọc từ `application.properties`, không hardcode), `SecurityConfiguration` verify bằng cùng key qua `NimbusJwtDecoder`.
10. **Validate trùng lặp dùng chung `validateXxx(value, currentId)`**: `currentId == null` là tạo mới, khác `null` là update — áp dụng cho Product, Coupon, User (email).
11. **Upload file dùng `multipart/form-data`**: JS dùng `FormData`, Spring dùng `@ModelAttribute` + `MultipartFile` ngay trong DTO. Coupon (không có ảnh) cũng đã đổi từ JSON thuần sang `FormData` để đồng bộ cách gọi API toàn hệ thống.
12. **Layout dùng file HTML nhỏ rồi fetch vào trang chính**: `header.html`, `sidebar.html`, nhúng qua `initAdminLayout()`.
13. **`admin-api.js` là lớp trung gian duy nhất gọi API**: tự gắn `Authorization: Bearer <token>`, tự unwrap `ApiResponse.result`, tự đăng xuất khi nhận 401.
14. **`ApplicationInitConfig` seed admin mặc định**: chỉ chạy khi bảng `users` rỗng, giải quyết bài toán "chưa có ai để đăng nhập tạo user ADMIN đầu tiên" vì mọi API tạo user đều yêu cầu quyền ADMIN.

---

## 7. Cách chạy dự án

### Yêu cầu

- Java 17, Maven/`mvnw`, MySQL `localhost:3306`, database `laptopshop`.

### Dependency cần có trong `pom.xml` (bổ sung từ v3)

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.3</version>
</dependency>
```

Và annotation processor cho MapStruct trong `maven-compiler-plugin` (`mapstruct-processor` cùng version).

### Cấu hình (`application.properties`)

```properties
# URL kết nối lên Cloud Aiven
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# Cloudinary Configuration
cloudinary.cloud-name=${CLOUDINARY_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}

jwt.signerKey=${JWT_SIGNER_KEY}
jwt.valid-duration=3600
```

### Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

Lần chạy đầu tiên (DB `users` rỗng), `ApplicationInitConfig` tự tạo tài khoản `admin@laptopshop.com` / `admin@123` — xem log console dòng cảnh báo để lấy mật khẩu, đổi ngay sau khi đăng nhập lần đầu.

### URL thường dùng

- Đăng nhập admin: `http://localhost:8080/admin/login.html`
- Dashboard: `http://localhost:8080/admin/dashboard/show.html`
- API đăng nhập: `POST http://localhost:8080/api/v1/auth/login`
- API users: `http://localhost:8080/api/v1/admin/users`
- API categories: `http://localhost:8080/api/v1/admin/categories`
- API products: `http://localhost:8080/api/v1/admin/products`
- API coupons: `http://localhost:8080/api/v1/admin/coupons`

---

## 8. Những thay đổi đã triển khai gần đây (từ v3 → v4)

1. **MapStruct + Response DTO cho cả 4 module**: `UserMapper`, `ProductMapper`, `CategoryMapper`, `CouponMapper` — mỗi mapper 2 chiều (DTO→Entity giữ tay field có logic đặc biệt; Entity→Response tự động). Controller không còn tự gọi mapper — Service trả thẳng `*Response`, mapping luôn nằm trong `@Transactional`.
2. **`CategoryDetailResponse`**: trang chi tiết Category nạp kèm `List<ProductResponse>` an toàn (tránh `LazyInitializationException`), trang danh sách dùng `CategoryResponse` gọn nhẹ không có `products`.
3. **JWT + Spring Security hoàn chỉnh**:
   - `AuthenticationService`: đăng nhập (so khớp BCrypt), phát hành JWT HS512, `introspect()` kiểm tra token còn hợp lệ.
   - `SecurityConfiguration`: `NimbusJwtDecoder` + `JwtAuthenticationConverter` (claim `scope` → quyền `ROLE_*`), phân quyền theo path thật (`/api/v1/admin/users/**`, `/coupons/**` yêu cầu `ROLE_ADMIN`; GET product/category public).
   - `JwtAuthenticationEntryPoint` (401) và `JwtAccessDeniedHandler` (403): xử lý ngay trong `SecurityFilterChain` vì 2 loại lỗi này xảy ra trước `DispatcherServlet`, `@RestControllerAdvice` không bắt được.
4. **User có nhiều Role**: `@ManyToOne Role` → `@ManyToMany Set<Role> roles` (bảng `user_roles`), `roleName` (String) → `roleNames` (`List<String>`) xuyên suốt DTO/Response/Mapper/JS. JWT claim `scope` giờ chứa nhiều role cách nhau bởi khoảng trắng.
5. **User xóa mềm**: `@SQLDelete`/`@Where` + field `deletedAt` ở `User.java`, bỏ `unique = true` ở cột `email`. `UserService.deleteUserById()` không đổi 1 dòng code nào — cơ chế nằm hoàn toàn ở Entity.
6. **`ApplicationInitConfig`**: seed tài khoản admin mặc định khi bảng `users` rỗng, tự tạo luôn Role `ADMIN` nếu chưa có.
7. **Giao diện User Admin**: `create.html`/`update.html` đổi từ `<select>` chọn 1 Role sang nhóm checkbox chọn nhiều Role; `detail.html`/`list.html` hiển thị nhiều badge role.
8. **Coupon đổi sang `FormData`**: đồng bộ cách gọi API với User/Product/Category (trước đó là JSON `@RequestBody`), `CouponRestController` đổi `@RequestBody` → `@ModelAttribute`.
9. **`admin-api.js`**: tự gắn `Authorization: Bearer <token>`, tự unwrap `ApiResponse.result`, tự đăng xuất khi nhận 401.
10. Đã đưa CSDL lên aiven.io và ảnh lên Cloudinary.

### Việc còn cần tự làm (đã dặn ở các bước trước, nhắc lại cho gọn)

- Bổ sung method còn thiếu ở `ProductRepository`/`CouponRepository`/`CategoryRepository` (`existsByCodeIgnoreCase`, `existsByCodeIgnoreCaseAndIdNot`...).
- Build lại (`mvnw.cmd -q -DskipTests compile`) để MapStruct sinh các `*MapperImpl`.
- Xác nhận `ProductCreationRequest`/`ProductUpdateRequest` xử lý field `category` đúng ý muốn (đang gán trực tiếp `request.getCategory()`, chưa chắc đã qua lookup DB).

---

## 9. Roadmap ưu tiên — góc nhìn Senior Backend, hướng tới đi thực tập/xin việc

### Phase 2 — Hoàn thiện luồng nghiệp vụ lõi (Order/Cart)

Phần **quan trọng nhất về mặt business logic** — chưa có luồng đặt hàng thì portfolio chưa "kể được câu chuyện" trọn vẹn:

1. **Cart**: đơn giản trước — lưu theo `User`, sau nâng cấp Redis nếu muốn thể hiện thêm kỹ năng.
2. **Order + OrderDetail**: `OrderService.createOrder()` cần `@Transactional` đúng cách — trừ tồn kho, tăng `Product.sold`, áp dụng `couponService.calculateDiscount()` đã có sẵn, tăng `Coupon.usedCount`, snapshot dữ liệu vào `OrderDetail`.
3. Cân nhắc áp dụng **xóa mềm cho Product/Category** trước khi làm Order — đơn hàng cũ sẽ tham chiếu Product/Category, xóa thật sẽ làm gãy lịch sử đơn hàng. Cách làm y hệt User (`@SQLDelete`/`@Where`), gần như không phải sửa Service/Controller.
4. Đây là chỗ tốt nhất thể hiện hiểu biết về **transaction, tính toàn vẹn dữ liệu, race condition** khi nhiều người cùng mua 1 sản phẩm gần hết hàng.

### Phase 3 — Chất lượng code & khả năng vận hành

1. **Unit test + Integration test** (JUnit 5, Mockito, `@SpringBootTest` + H2/Testcontainers) cho `CouponService`, `AuthenticationService`, `OrderService` sau này.
2. **Pagination + filter + sort** cho Product/Order (`Pageable`) — tránh `findAll()` không giới hạn.
3. **Flyway/Liquibase** thay `ddl-auto=update`.
4. **Swagger/OpenAPI** (`springdoc-openapi`) — càng cần thiết hơn giờ đã có JWT (Swagger UI hỗ trợ nhập Bearer token để test).
5. **Logging có cấu trúc** (SLF4J + Logback).
6. **Refresh token / thu hồi token khi logout** — hiện `logout()` phía JS chỉ xóa token khỏi `localStorage`, token cũ vẫn còn hạn sử dụng được nếu ai đó giữ lại. Cần Redis blacklist hoặc refresh-token pattern nếu muốn chuẩn production.

### Phase 4 — Điểm cộng để nổi bật (làm nếu còn thời gian)

1. **Thanh toán thật** (VNPay/Momo sandbox)
2. **Dockerize**: `Dockerfile` + `docker-compose.yml` (app + MySQL).
3. **CI cơ bản** (GitHub Actions chạy `mvn test`).
4. **Redis cache** cho danh sách sản phẩm/category.

### Checklist rút gọn

1. ✅ ~~User/Product/Category/Coupon CRUD chuẩn DTO~~
2. ✅ ~~MapStruct + Response DTO 2 chiều, mapping trong `@Transactional`~~
3. ✅ ~~Spring Security + JWT + phân quyền + xử lý 401/403~~
4. ✅ ~~User nhiều Role + xóa mềm~~
5. ⬜ Cart + Order + OrderDetail (kèm `@Transactional`)
6. ⬜ Xóa mềm cho Product/Category (nếu làm Order trước, nên làm bước này song song)
7. ⬜ Pagination cho Product/Order
8. ⬜ Unit test cho Service quan trọng
9. ⬜ Swagger + Dockerize
10. ⬜ Refresh token / thu hồi token khi logout
11. ⬜ (Tùy thời gian) Thanh toán thật, Redis cache, CI

Nền tảng (xử lý lỗi, bảo mật) đã xong — **ưu tiên tiếp theo rõ ràng là Order/Cart** (mục 5), đây là mảnh ghép nghiệp vụ lớn nhất còn thiếu để dự án "kể được câu chuyện" hoàn chỉnh của 1 shop bán hàng thật sự.

---
