# Tổng quan dự án — Laptopshop

> Bản cập nhật (v5) — tính từ v4: **tách hẳn Backend/Frontend thành 2 repo độc lập** (Backend: Spring Boot REST API thuần; Frontend: chuyển từ HTML/Bootstrap tĩnh sang **Angular**, repo riêng `laptopshop-frontend`), **JWT logout + refresh token qua Redis** (blacklist cho access token, whitelist + rotation cho refresh token), và **module Role–Permission** (quan hệ nhiều-nhiều, CRUD đầy đủ). Tài liệu này từ v5 trở đi chỉ mô tả **Backend** — cấu trúc/quy ước của Frontend Angular nằm ở tài liệu riêng trong repo `laptopshop-frontend`.

---

## 1. Kiến trúc tổng thể

```text
Frontend (repo riêng — laptopshop-frontend, Angular)
        │ HttpClient + Interceptor gắn Authorization: Bearer <accessToken>
        │ (CORS — khác origin với Backend)
        ▼
Spring Security (CustomJwtDecoder kiểm tra chữ ký + hạn dùng + blacklist Redis)
        │
        ▼
@RestController → Service (@Transactional) → Mapper (MapStruct) → Repository → JPA/Hibernate → MySQL (aiven.io)
        │
        └── AuthenticationService ──► Redis (InvalidatedToken: blacklist access token
                                              RefreshToken: whitelist + rotation)
```

- **Frontend**: repo riêng (`laptopshop-frontend`, Angular), gọi API qua `HttpClient`, tự gắn `Authorization: Bearer <accessToken>` qua `HttpInterceptor`, token/refreshToken lưu ở `localStorage`. Không còn nằm trong `src/main/resources/static/` của Backend nữa.
- **Backend**: chỉ còn trả JSON qua `/api/v1/**`, bọc trong `ApiResponse<T>` thống nhất (`code`, `message`, `result`). Không còn `ViewController`/route trả HTML — đã xóa cùng đợt tách repo (backend không phục vụ file tĩnh nào nữa).
- **CORS**: bắt buộc phải có từ v5 vì Frontend/Backend khác origin — cấu hình qua `CorsConfig` + `httpSecurity.cors(...)` trong `SecurityConfiguration`.
- **Bảo mật — access token**: mọi request (trừ `/api/v1/admin/auth/**` và GET sản phẩm/danh mục) bắt buộc JWT hợp lệ; phân quyền theo Role qua `hasRole(...)`. `CustomJwtDecoder` kiểm tra thêm **blacklist Redis** trước khi verify chữ ký — token đã logout thì bị từ chối dù chưa hết hạn tự nhiên.
- **Bảo mật — refresh token**: cơ chế **whitelist ngược lại** access token — refresh token phải **tồn tại** trong Redis (`RefreshTokenRepository`) mới hợp lệ. Mỗi lần gọi `/auth/refresh` thành công, refresh token cũ bị xóa ngay (rotation, chống replay), phát cặp token mới; hạn tuyệt đối (`absoluteExpiry`) giữ nguyên từ lần login gốc, không bị kéo dài vô hạn dù refresh liên tục.
- **Chuyển đổi dữ liệu**: MapStruct đảm nhiệm cả 2 chiều — DTO → Entity (field thuần túy) và Entity → Response DTO — luôn chạy **bên trong `@Transactional` của Service**, không phải ở Controller, để tránh `LazyInitializationException` với các quan hệ `@ManyToOne`/`@ManyToMany`.
- **Xóa mềm**: áp dụng cho `User` (nhiều Role) qua `@SQLDelete` + `@Where` ở tầng Entity — Service/Controller gọi `delete()` y hệt như xóa thật, Hibernate tự đổi thành `UPDATE deleted_at = NOW()`.
- **Ảnh upload động**: lưu trên Cloudinary (không còn lưu đĩa local) — Service gọi Cloudinary SDK, lưu URL đầy đủ (`https://res.cloudinary.com/...`) thẳng vào cột `avatar`/`image`. Frontend dùng nguyên URL trả về, không cần ghép domain Backend.
- **Xử lý lỗi nghiệp vụ**: `AppException` + enum `ErrorCode` (mã số + message tiếng Việt + HttpStatus). Lỗi 401/403 xử lý riêng trong `SecurityConfiguration` (`JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`) vì xảy ra trước khi request chạm `DispatcherServlet`.

Cách hiểu dễ nhớ (đã có thêm 2 kho Redis, ngoài các tầng cũ):

- `domain`: bản thiết kế bảng (MySQL) **và** model Redis (`InvalidatedToken`, `RefreshToken` — `@RedisHash`).
- `repository`: cửa vào database — gồm cả `JpaRepository` (MySQL) và `CrudRepository` (Redis, cho 2 model trên).
- `service`: nơi xử lý nghiệp vụ + validate + ném `AppException`, đồng thời là nơi **duy nhất** gọi `mapper` để convert Entity ↔ DTO (trong `@Transactional`).
- `mapper`: interface MapStruct, sinh code convert tại compile-time, không dùng reflection.
- `dto/request`: nhận dữ liệu thô từ Controller.
- `dto/response`: hình dạng dữ liệu trả ra ngoài, **không** phải Entity.
- `RestController`: chỉ gọi Service rồi bọc kết quả (đã là DTO) vào `ApiResponse<T>`.
- `config`: `SecurityConfiguration`, `CorsConfig` (mới), `CustomJwtDecoder` (mới — thay `NimbusJwtDecoder` bean trực tiếp), `JwtAuthenticationEntryPoint` (401), `JwtAccessDeniedHandler` (403), `ApplicationInitConfig` (seed admin).

---

## 2. Cấu trúc hiện tại (Backend — repo `laptopshop-backend`)

```text
laptopshop-backend/
├── .gitignore, .gitattributes
├── pom.xml                                  ⚠️ cần có mapstruct, mapstruct-processor,
│                                                spring-boot-starter-oauth2-resource-server,
│                                                nimbus-jose-jwt, spring-boot-starter-data-redis (mới)
├── docker-compose.yml                        ✅ mới — chạy Redis local (redis:7-alpine)
├── src/main/java/com/example/laptopshop/
│   ├── config/
│   │   ├── SecurityConfiguration.java        ✅ JWT resource server + phân quyền theo path/Role + CORS
│   │   ├── CorsConfig.java                   ✅ mới — bắt buộc từ khi tách FE/BE khác origin
│   │   ├── CustomJwtDecoder.java             ✅ mới — kiểm tra blacklist Redis trước khi verify chữ ký
│   │   ├── JwtAuthenticationEntryPoint.java  ✅ xử lý 401 (thiếu/sai/hết hạn/đã logout)
│   │   ├── JwtAccessDeniedHandler.java       ✅ xử lý 403 (đủ token nhưng sai quyền)
│   │   └── ApplicationInitConfig.java        ✅ seed tài khoản admin mặc định nếu DB user rỗng
│   ├── controller/api/
│   │   ├── AuthenticationController.java     ✅ POST /auth/login, /auth/introspect, /auth/logout (mới), /auth/refresh (mới)
│   │   ├── UserRestController.java           ✅ CRUD User, trả UserResponse (roleNames: List<String>)
│   │   ├── RoleRestController.java           ✅ CRUD đầy đủ (mới — trước chỉ có list), nhận permissionNames
│   │   ├── PermissionRestController.java     ✅ mới — CRUD Permission
│   │   ├── ProductRestController.java        ✅ CRUD Product, trả ProductResponse
│   │   ├── CategoryRestController.java       ✅ CRUD Category, GET /{id} trả CategoryDetailResponse
│   │   └── CouponRestController.java         ✅ CRUD Coupon, trả CouponResponse
│   │   (❌ ViewController.java — đã xóa, không còn phục vụ HTML nữa)
│   ├── domain/
│   │   ├── User.java                 ✅ roles: Set<Role> (@ManyToMany), deletedAt, @SQLDelete/@Where
│   │   ├── Role.java                 ✅ + permissions: Set<Permission> (@ManyToMany, bảng role_permissions) — mới
│   │   ├── Permission.java           ✅ mới — id, name, description
│   │   ├── Product.java, Category.java, Coupon.java
│   │   ├── Order.java, OrderDetail.java, OrderStatus.java   ⚠️ mới có entity
│   │   ├── InvalidatedToken.java     ✅ mới — @RedisHash, blacklist access token (id=jwtId, ttl)
│   │   └── RefreshToken.java         ✅ mới — @RedisHash, whitelist refresh token (id=jwtId, userId có @Indexed, ttl)
│   ├── dto/
│   │   ├── request/
│   │   │   ├── Auth/AuthenticationRequest.java, IntrospectRequest.java,
│   │   │   │        LogoutRequest.java (mới), RefreshTokenRequest.java (mới)
│   │   │   ├── User/UserCreationRequest.java, UserUpdateRequest.java (roleNames: List<String>)
│   │   │   ├── Role/RoleCreationRequest.java, RoleUpdateRequest.java (permissionNames: List<String>) — mới
│   │   │   ├── Permission/PermissionCreationRequest.java, PermissionUpdateRequest.java — mới
│   │   │   ├── Product/…, Category/…, Coupon/…
│   │   └── response/
│   │       ├── ApiResponse.java
│   │       ├── AuthenticationResponse.java   ✅ + field refreshToken (mới)
│   │       ├── IntrospectResponse.java
│   │       ├── User/UserResponse.java
│   │       ├── Role/RoleResponse.java        ✅ + permissionNames: List<String> — mới
│   │       ├── Permission/PermissionResponse.java — mới
│   │       ├── Product/…, Category/…, Coupon/…
│   ├── mapper/
│   │   ├── UserMapper.java, ProductMapper.java, CategoryMapper.java, CouponMapper.java
│   │   ├── RoleMapper.java           ✅ viết lại — permissions → permissionNames qua helper method
│   │   └── PermissionMapper.java     — mới
│   ├── exception/
│   │   ├── AppException.java
│   │   └── ErrorCode.java            ✅ + ROLE_NAME_EXISTED, PERMISSION_NOT_FOUND, PERMISSION_NAME_EXISTED,
│   │                                     TOKEN_EMPTY, REFRESH_TOKEN_EXPIRED, REFRESH_TOKEN_NOT_FOUND (mới)
│   ├── repository/
│   │   ├── UserRepository.java, RoleRepository.java, PermissionRepository.java (mới)
│   │   ├── ProductRepository.java, CategoryRepository.java, CouponRepository.java
│   │   ├── InvalidatedTokenRepository.java   ✅ mới — CrudRepository<InvalidatedToken, String>
│   │   └── RefreshTokenRepository.java       ✅ mới — CrudRepository<RefreshToken, String> + findByUserId
│   └── service/
│       ├── AuthenticationService.java  ✅ viết lại — issueTokenPair(), refreshToken() (rotation),
│       │                                   logout() (blacklist access + xóa refresh), verifyToken()/verifyRefreshToken()
│       ├── UserService.java
│       ├── RoleService.java            ✅ mới — CRUD Role theo list permissionNames
│       ├── PermissionService.java      — mới
│       ├── UploadService.java          ✅ giờ chỉ gọi Cloudinary SDK, không ghi đĩa local nữa
│       ├── ProductService.java, CategoryService.java, CouponService.java
└── src/main/resources/
    └── application.properties        ✅ + spring.data.redis.host/port, jwt.refreshable-duration (mới)
    (❌ static/ — đã xóa toàn bộ, chuyển sang repo laptopshop-frontend)
```

**Frontend (repo riêng `laptopshop-frontend`)** — Angular, không còn mô tả chi tiết ở tài liệu này. Ghi chú nhanh cho việc chuyển đổi từ bản HTML/Bootstrap cũ:
- `admin-api.js` (`apiRequest`, `AuthAPI`, `UserAPI`, …) → chuyển thành `AuthService`, `UserService`… dùng `HttpClient`, interceptor thay cho việc tự gắn header tay.
- `admin-guard.js` (đọc payload JWT, chặn vào trang không phải ADMIN) → `CanActivate`/`CanMatch` Guard của Angular Router.
- `localStorage.getItem('accessToken')`/`refreshToken` — giữ nguyên ý tưởng, có thể bọc trong 1 `TokenStorageService` riêng.
- Luồng gọi `/auth/refresh` khi 401 nên làm qua `HttpInterceptor` (retry request gốc sau khi refresh xong) — đây là phần còn thiếu, xem mục Roadmap.

---

## 3. Sơ đồ bảng / Entity Domain

| Entity / Enum | Bảng DB / Kho          | Vai trò                                                |
| -------------- | ---------------------- | ------------------------------------------------------ |
| `Role`         | `roles`                | Vai trò tài khoản                                       |
| `Permission`   | `permissions`          | Quyền hạn chi tiết — **mới**                            |
| `User`         | `users`                | Người dùng / quản trị viên — có xóa mềm (`deleted_at`)  |
| `Category`     | `categories`           | Danh mục hoặc nhóm sản phẩm                             |
| `Product`      | `products`              | Sản phẩm bán trong shop                                 |
| `Order`        | `orders`                | Đơn hàng                                                 |
| `OrderDetail`  | `order_detail`          | Chi tiết từng dòng sản phẩm trong đơn                    |
| `Coupon`       | `coupons`                | Mã giảm giá                                              |
| `OrderStatus`  | enum                     | Trạng thái đơn hàng                                      |
| `InvalidatedToken` | Redis (`INVALIDATED_TOKEN`) | Blacklist access token đã logout — **mới**       |
| `RefreshToken` | Redis (`REFRESH_TOKEN`) | Whitelist refresh token còn hiệu lực — **mới**            |

Quan hệ giữa các bảng (đã thêm Role-Permission):

```text
users      n ──── n roles          (bảng trung gian user_roles)
roles      n ──── n permissions    (bảng trung gian role_permissions)   -- mới
users      1 ──── n orders
categories 1 ──── n products
orders     1 ──── n order_detail
products   1 ──── n order_detail
coupons    1 ──── n orders          (orders.coupon_id có thể null)
```

Điểm cần nhớ (bổ sung so với v4):

- `Role` giờ có `Set<Permission> permissions` (`@ManyToMany`, bảng `role_permissions`, tự tạo nhờ `@JoinTable` — không cần entity trung gian riêng). JWT claim `permissions` (claim mới, tách khỏi `scope`) gom permission từ **tất cả** role của user, loại trùng, cách nhau bởi khoảng trắng.
- `JwtAuthenticationConverter` giờ gộp 2 nguồn quyền: claim `scope` → `ROLE_*` (dùng `hasRole`), claim `permissions` → giữ nguyên tên, không prefix (dùng `hasAuthority`).
- 2 kho Redis hoạt động **ngược logic nhau**: `InvalidatedToken` là **blacklist** (không tồn tại = hợp lệ), `RefreshToken` là **whitelist** (phải tồn tại mới hợp lệ). TTL của `InvalidatedToken` = hạn còn lại của access token; TTL của `RefreshToken` = hạn tuyệt đối của refresh token, không bị gia hạn thêm mỗi lần refresh.

---

## 4. Trạng thái từng module

| Module              | Backend                                                                   | Frontend (Angular — repo riêng)      | Ghi chú                                                        |
| -------------------- | -------------------------------------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------- |
| **Auth (JWT + Redis)** | Đầy đủ — login, introspect, logout (blacklist), refresh (whitelist + rotation) | Đang chuyển sang Angular | Việc còn thiếu: interceptor tự refresh khi 401 (xem Roadmap)   |
| **Role / Permission** | Đầy đủ — CRUD cả 2, quan hệ nhiều-nhiều | Chưa có UI bên Angular | Backend xong hoàn toàn, JWT đã mang claim `permissions`         |
| **User Admin**       | Đầy đủ (DTO + MapStruct + Transactional + nhiều Role + xóa mềm)         | Đang chuyển sang Angular          | Module tham chiếu đầy đủ nhất phía Backend                       |
| **Dashboard Admin**  | Cơ bản                                                                   | Đang chuyển sang Angular          | Product/Order còn cần số liệu thật                                |
| **Category Admin**   | Đầy đủ (detail kèm danh sách Product)                                    | Đang chuyển sang Angular          |                                                                     |
| **Product Admin**    | Đầy đủ                                                                   | Đang chuyển sang Angular          |                                                                     |
| **Coupon**           | Đầy đủ (2 kiểu giảm giá)                                                 | Đang chuyển sang Angular          |                                                                     |
| **Order Admin**      | Mới có entity                                                            | Chưa có                            | Module lớn tiếp theo — xem roadmap                                 |
| **OrderDetail**      | Mới có entity                                                            | —                                      | Field snapshot đã có sẵn                                           |
| **Client (site bán hàng)** | Có thể dùng Product API                                          | Chưa bắt đầu bên Angular            | Cần dựng lại từ đầu bên repo Angular                                |
| **Cart**             | Chưa có                                                                  | Chưa có                             | Cần thiết kế riêng                                                  |
| **Upload ảnh**       | Cloudinary                                                               | —                                      | Đã bỏ hẳn lưu đĩa local                                              |
| **CORS**             | `CorsConfig` + Security                                                  | —                                      | Bắt buộc từ khi tách repo, khác origin                               |

---

## 5. Luồng học dự án nên đi theo

1. **Domain**: bảng MySQL nào tồn tại (chú ý User–Role–Permission giờ đều nhiều-nhiều), 2 model Redis (`InvalidatedToken`, `RefreshToken`) khác gì bảng MySQL thường.
2. **Repository**: `JpaRepository` (MySQL) vs `CrudRepository` (Redis) — khác nhau ở đâu, vì sao Redis dùng `CrudRepository`.
3. **Service**: nơi xử lý nghiệp vụ + validate + ném `AppException` + **gọi Mapper trong `@Transactional`**. Riêng `AuthenticationService`: hiểu rõ khác biệt blacklist (access token) vs whitelist (refresh token) trước khi đọc code.
4. **Mapper (MapStruct)**: vì sao tách 2 chiều rõ ràng.
5. **DTO**: Controller không hứng trực tiếp Entity, không trả trực tiếp Entity.
6. **RestController**: chỉ gọi Service, bọc `ApiResponse<T>`.
7. **Security**: JWT verify ở tầng filter (`CustomJwtDecoder`) trước khi tới Controller — có bước kiểm tra Redis blacklist thêm so với `NimbusJwtDecoder` mặc định; phân quyền theo path + Role + Permission (`hasRole`/`hasAuthority`).
8. **CORS**: vì sao bắt buộc phải có từ khi tách repo, khác với lúc còn chung 1 origin.
9. **Frontend (Angular, repo riêng)**: interceptor tự gắn token, tự retry khi 401 qua refresh token, guard chặn route theo `scope`/`permissions` trong JWT.

Module tham khảo đầy đủ nhất hiện tại (Backend): **Auth + Role/Permission** — thể hiện rõ nhất toàn bộ pattern mới (Redis 2 chiều, quan hệ nhiều-nhiều, rotation).

---

## 6. Các quyết định kiến trúc quan trọng (bổ sung từ v4)

15. **Tách Backend/Frontend thành 2 repo độc lập**: không còn "over-engineer" khi còn dùng chung 1 origin — đánh đổi lấy khả năng deploy độc lập, đúng chuẩn thực tế doanh nghiệp (BE lên Render/Railway, FE lên Vercel/Netlify), nhưng phải đánh đổi thêm độ phức tạp CORS.
16. **Frontend chuyển từ HTML/Bootstrap tĩnh sang Angular**: lý do — cần thể hiện kỹ năng SPA framework thực tế (routing, state, reactive forms, HttpClient/interceptor) thay vì thao tác DOM tay; đánh đổi: phải viết lại toàn bộ UI đã có từ đầu.
17. **`CustomJwtDecoder` thay `NimbusJwtDecoder` bean trực tiếp**: chèn thêm bước kiểm tra blacklist Redis trước khi verify chữ ký — lý do đặt trước verify: token đã logout thì không cần tốn công verify chữ ký nữa, fail nhanh hơn.
18. **2 cơ chế Redis ngược nhau cho 2 loại token**: access token dùng **blacklist** (mặc định tin tưởng, vì sống ngắn), refresh token dùng **whitelist** (mặc định không tin tưởng, vì sống dài và rủi ro cao hơn nếu bị đánh cắp) — đây là quyết định có chủ đích, không phải thiếu nhất quán.
19. **Refresh token rotation + hạn tuyệt đối cố định**: mỗi lần refresh, token cũ bị xóa ngay (chống replay), nhưng hạn hết hiệu lực (`expirationTime`) giữ nguyên từ lần login gốc — tránh phiên đăng nhập kéo dài vô hạn nếu người dùng cứ refresh liên tục.
20. **Role–Permission không cần entity trung gian riêng**: chỉ khai báo `@ManyToMany` + `@JoinTable` ở `Role`, Hibernate tự tạo bảng `role_permissions` — giống hệt cách làm `user_roles`.
21. **JWT tách riêng claim `scope` (Role) và `permissions`**: tránh nhầm lẫn khi cả 2 loại quyền dùng chung 1 claim với cùng prefix `ROLE_` — mỗi claim có `JwtGrantedAuthoritiesConverter` riêng, gộp lại trong `jwtAuthenticationConverter()`.
22. **Ảnh upload chuyển hẳn sang Cloudinary**: bỏ hoàn toàn cơ chế lưu đĩa local + serve qua `/images-upload/**` — đơn giản hóa việc tách Frontend/Backend (không còn phải lo domain ảnh trỏ về Backend).

*(1–14: xem lại các quyết định nền tảng từ v4 — REST + ApiResponse, DTO 2 chiều, MapStruct trong Service, xóa mềm, ErrorCode theo dải mã module, JWT HS512, validate trùng lặp `validateXxx(value, currentId)`, upload multipart... vẫn giữ nguyên, không đổi.)*

---

## 7. Cách chạy dự án

### Yêu cầu

- Java 17, Maven/`mvnw`, MySQL (aiven.io — connection string qua biến môi trường), **Redis** (mới — chạy local qua `docker-compose up -d` hoặc Redis cloud).
- Node.js + npm (cho repo `laptopshop-frontend` chạy Angular CLI — xem tài liệu riêng của repo đó).

### Dependency mới trong `pom.xml` (bổ sung từ v4)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### `docker-compose.yml` (mới, ở gốc repo backend)

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: laptopshop-redis
    ports:
      - "6379:6379"
```

### Cấu hình (`application.properties`) — bổ sung từ v4

```properties
# Redis
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

# JWT — thêm refreshable-duration bên cạnh valid-duration đã có
jwt.refreshable-duration=${JWT_REFRESHABLE_DURATION:1209600}
```

### Chạy ứng dụng

```bash
docker compose up -d      # bật Redis trước
./mvnw spring-boot:run
```

Lần chạy đầu tiên (DB `users` rỗng), `ApplicationInitConfig` tự tạo tài khoản `admin@laptopshop.com` / `admin@123` — đổi ngay sau khi đăng nhập lần đầu.

### URL thường dùng (Backend — không còn URL trang HTML nào, toàn bộ là API)

- API đăng nhập: `POST http://localhost:8080/api/v1/admin/auth/login`
- API đăng xuất: `POST http://localhost:8080/api/v1/admin/auth/logout`
- API làm mới token: `POST http://localhost:8080/api/v1/admin/auth/refresh`
- API users: `http://localhost:8080/api/v1/admin/users`
- API roles: `http://localhost:8080/api/v1/admin/roles`
- API permissions: `http://localhost:8080/api/v1/admin/permissions`
- API categories/products/coupons: giữ nguyên như v4

Frontend (Angular) chạy riêng qua `ng serve` trong repo `laptopshop-frontend` — xem README của repo đó.

---

## 8. Những thay đổi đã triển khai (từ v4 → v5)

1. **Tách repo**: `laptopshop-backend` (Spring Boot thuần REST API) và `laptopshop-frontend` (Angular) — không còn `static/`, không còn `ViewController`.
2. **CORS**: thêm `CorsConfig` + bật `.cors(...)` trong `SecurityConfiguration` — bắt buộc vì 2 repo chạy khác origin.
3. **Module Role–Permission hoàn chỉnh**: `Permission` entity mới, `Role` thêm quan hệ `@ManyToMany` tới `Permission`, CRUD đầy đủ 2 chiều (`RoleService`/`PermissionService`, `RoleRestController`/`PermissionRestController`, `RoleMapper`/`PermissionMapper`).
4. **JWT logout qua Redis (blacklist)**: `InvalidatedToken` (`@RedisHash`), `CustomJwtDecoder` kiểm tra trước khi verify chữ ký, endpoint `POST /auth/logout`.
5. **Refresh token qua Redis (whitelist + rotation)**: `RefreshToken` (`@RedisHash`, có `@Indexed userId` để hỗ trợ revoke-all-device), endpoint `POST /auth/refresh`, hạn tuyệt đối cố định không bị gia hạn qua mỗi lần refresh.
6. **JWT claim tách `scope` (Role) và `permissions`**: `jwtAuthenticationConverter()` gộp 2 nguồn quyền, `hasRole()` dùng claim `scope`, `hasAuthority()` dùng claim `permissions`.
7. **Ảnh chuyển hẳn sang Cloudinary**: bỏ cơ chế lưu đĩa local + endpoint serve ảnh tĩnh.
8. **CSDL trên aiven.io** (đã có từ trước v4, xác nhận lại vì liên quan tới việc tách repo — không còn phụ thuộc máy local nào để chạy Backend ngoài Redis).

### Việc còn cần tự làm

- Frontend (Angular): dựng `HttpInterceptor` tự động gọi `/auth/refresh` khi gặp 401 rồi retry request gốc — hiện Backend đã sẵn sàng, chỉ còn thiếu phía client.
- `revokeAllRefreshTokens(userId)` đã có sẵn ở `AuthenticationService` nhưng chưa có nơi gọi tới — cần gắn vào tính năng đổi mật khẩu khi làm (hiện `UserUpdateRequest` chưa có field password).
- Dựng lại toàn bộ UI Admin bằng Angular (User/Role/Permission/Category/Product/Coupon) — Backend cho các module này đã sẵn sàng 100%.
- Xác nhận lại `ErrorCode` đã thêm đủ mã cho: `TOKEN_EMPTY`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_NOT_FOUND`, `ROLE_NAME_EXISTED`, `ROLE_PERMISSIONS_EMPTY`, `PERMISSION_NOT_FOUND`, `PERMISSION_NAME_EXISTED`.

---

## 9. Roadmap ưu tiên — góc nhìn Senior Backend, hướng tới đi thực tập/xin việc

### Phase 2 — Hoàn thiện luồng nghiệp vụ lõi (Order/Cart)

Vẫn là phần **quan trọng nhất** — chưa đổi so với v4:

1. **Cart**: lưu theo `User`, cân nhắc Redis nếu muốn thể hiện thêm kỹ năng (đã có kinh nghiệm Redis từ phần Auth).
2. **Order + OrderDetail**: `OrderService.createOrder()` `@Transactional` đúng cách — trừ tồn kho, tăng `Product.sold`, áp dụng `couponService.calculateDiscount()`, tăng `Coupon.usedCount`, snapshot vào `OrderDetail`.
3. Cân nhắc xóa mềm cho Product/Category trước khi làm Order (giống `User`, dùng `@SQLDelete`/`@Where`).
4. Chỗ tốt nhất thể hiện hiểu biết về **transaction, tính toàn vẹn dữ liệu, race condition**.

### Phase 3 — Chất lượng code & khả năng vận hành

1. **Unit test + Integration test** (JUnit 5, Mockito, `@SpringBootTest` + H2/Testcontainers) — nay có thêm `AuthenticationService` (login/refresh/logout) là ứng viên tốt để viết test đầu tiên, vì logic rõ ràng, dễ mock Redis repository.
2. **Pagination + filter + sort** cho Product/Order.
3. **Flyway/Liquibase** thay `ddl-auto=update`.
4. **Swagger/OpenAPI** — càng cần thiết hơn vì giờ có 2 loại token (access/refresh), Swagger UI hỗ trợ nhập Bearer token để test cả 2 luồng.
5. **Logging có cấu trúc** (SLF4J + Logback) — đã bắt đầu áp dụng ở `AuthenticationService` (`@Slf4j`), mở rộng ra các Service còn lại.
6. ~~Refresh token / thu hồi token khi logout~~ Đã xong — chuyển xuống checklist.

### Phase 4 — Điểm cộng để nổi bật (làm nếu còn thời gian)

1. **Thanh toán thật** (VNPay/Momo sandbox)
2. **Dockerize toàn bộ**: `docker-compose.yml` giờ đã có Redis, mở rộng thêm app + MySQL (hoặc giữ MySQL trên aiven.io) để 1 lệnh chạy được cả hệ thống.
3. **CI cơ bản** (GitHub Actions chạy `mvn test`) — cả 2 repo (Backend/Frontend) nên có pipeline riêng vì đã tách repo.
4. **Redis cache** cho danh sách sản phẩm/category — tận dụng Redis đã có sẵn từ phần Auth, không cần setup thêm hạ tầng.

### Checklist rút gọn

1. ~~User/Product/Category/Coupon CRUD chuẩn DTO~~ — Xong
2. ~~MapStruct + Response DTO 2 chiều, mapping trong `@Transactional`~~ — Xong
3. ~~Spring Security + JWT + phân quyền + xử lý 401/403~~ — Xong
4. ~~User nhiều Role + xóa mềm~~ — Xong
5. ~~Role–Permission (nhiều-nhiều) + CRUD đầy đủ~~ — Xong
6. ~~Refresh token / thu hồi token khi logout (Redis blacklist + whitelist)~~ — Xong
7. ~~Tách Backend/Frontend thành 2 repo, CORS~~ — Xong
8. Cart + Order + OrderDetail (kèm `@Transactional`) — Chưa làm
9. Xóa mềm cho Product/Category — Chưa làm
10. Pagination cho Product/Order — Chưa làm
11. Unit test cho Service quan trọng — Chưa làm
12. Swagger + Dockerize toàn bộ — Chưa làm
13. Dựng lại UI Admin bằng Angular — Đang bắt đầu
14. Interceptor tự refresh token phía Angular khi gặp 401 — Chưa làm
15. (Tùy thời gian) Thanh toán thật, Redis cache, CI — Chưa làm

Nền tảng (xử lý lỗi, bảo mật, tách kiến trúc BE/FE) đã rất vững — **ưu tiên tiếp theo rõ ràng vẫn là Order/Cart** (mục 8), song song với việc dựng lại UI Angular (mục 13) để có thể demo trọn vẹn luồng mua hàng.

---
