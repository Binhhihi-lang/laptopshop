# Tổng quan dự án — Laptopshop

> Bản cập nhật theo code hiện tại sau khi rà soát lại `domain`, `repository`, `service`, `controller/api` và frontend tĩnh. Dự án đang đi theo hướng **Spring Boot REST API + HTML/Bootstrap/JS tĩnh**, không dùng JSP cho giao diện mới.

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
- **Backend**: trả JSON qua các API dưới `/api/v1/**`.
- **Database**: các class trong `domain/` là bản thiết kế bảng MySQL thông qua JPA/Hibernate.
- **Ảnh upload động**: lưu ngoài project tại `D:/Spring/laptopshop/uploads/`, serve qua `/images-upload/**`.

Cách hiểu dễ nhớ:

- `domain`: bản thiết kế bảng.
- `repository`: cửa vào database.
- `service`: nơi xử lý nghiệp vụ.
- `RestController`: cổng API nhận request và trả JSON.
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
│   │       ├── UserRestController.java    ✅ CRUD User
│   │       ├── RoleRestController.java    ✅ danh sách Role
│   │       └── ProductRestController.java ✅ CRUD Product cơ bản
│   ├── domain/
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── Product.java
│   │   ├── Category.java
│   │   ├── Order.java
│   │   ├── OrderDetail.java
│   │   ├── Coupon.java
│   │   └── OrderStatus.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── RoleRepository.java
│   │   └── ProductRepository.java
│   └── service/
│       ├── UserService.java
│       ├── UploadService.java
│       └── ProductService.java
└── src/main/resources/
    ├── application.properties
    └── static/
        ├── admin/dashboard/show.html
        ├── admin/layout/header.html, sidebar.html
        ├── admin/user/list.html, create.html, update.html, detail.html
        ├── client/home.html                 ⚠️ hiện chưa có nội dung đáng kể
        ├── css/admin-style.css, client-style.css
        └── js/scripts.js, admin-api.js, client-api.js ⚠️ client-api.js còn trống/chưa đáng kể
```

---

## 3. Sơ đồ bảng / Entity Domain

Hiện tại `domain/` có 8 thành phần chính:

| Entity / Enum | Bảng DB        | Vai trò                               |
| ------------- | -------------- | ------------------------------------- |
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

- `Product`: `code`, `name`, `price`, `image`, `detailDesc`, `shortDesc`, `quantity`, `sold`, `factory`, `target`, `active`, `createdAt`, `category`.
- `Order`: `orderCode`, `totalPrice`, `discountAmount`, `orderDate`, `status`, `coupon`, `user`, `orderDetails`.
- `Coupon`: `code`, `discountPercent`, `expiryDate`, `usageLimit`, `usedCount`, `active`.

Điểm cần nhớ: `Order.java` và `Coupon.java` hiện mới có field/annotation và TODO, chưa có getter/setter đầy đủ. Khi làm module Order/Coupon, cần hoàn thiện trước.

---

## 4. Trạng thái từng module

| Module              | Backend                                 | Frontend                                  | Ghi chú                                                |
| ------------------- | --------------------------------------- | ----------------------------------------- | ------------------------------------------------------ |
| **User Admin**      | ✅ Đầy đủ                               | ✅ Đầy đủ                                 | Module mẫu hoàn chỉnh nhất                             |
| **Role**            | ✅ Đầy đủ                               | —                                         | Phục vụ dropdown chọn vai trò                          |
| **Dashboard Admin** | ✅ Cơ bản                               | ✅ Cơ bản                                 | Product/Order còn cần số liệu thật                     |
| **Product Admin**   | ✅ Controller/Service/Repository cơ bản | có rồi                                    | Bước nên làm tiếp theo                                 |
| **Category**        | Có rồi                                  | có rồi                                    | Product đã có quan hệ category nhưng chưa có API riêng |
| **Order Admin**     | ⚠️ Mới có entity                        | ❌ Chưa có                                | Chưa có repository/service/controller                  |
| **OrderDetail**     | ⚠️ Mới có entity                        | —                                         | Phục vụ chi tiết đơn hàng                              |
| **Coupon**          | ⚠️ Mới có entity                        | ❌ Chưa có                                | Chưa có getter/setter và chưa có API                   |
| **Client Home**     | ⚠️ Có thể dùng Product API              | ⚠️ `home.html` còn trống/chưa đáng kể     | Cần dựng giao diện và gọi API                          |
| **Client API JS**   | —                                       | ⚠️ `client-api.js` còn trống/chưa đáng kể | Cần thêm hàm gọi API phía client                       |
| **Cart**            | ❌ Chưa có                              | ❌ Chưa có                                | Cần thiết kế riêng                                     |

---

## 5. Luồng học dự án nên đi theo

Nên học theo thứ tự:

1. **Domain**: hiểu bảng nào tồn tại và chúng liên hệ với nhau thế nào.
2. **Repository**: hiểu Spring Data JPA giúp đọc/ghi database ra sao.
3. **Service**: hiểu nơi gom xử lý nghiệp vụ.
4. **RestController**: hiểu URL API nào nhận request và trả JSON.
5. **Frontend tĩnh**: hiểu HTML/JS gọi API bằng `fetch()` như thế nào.

Module nên học đầu tiên là **User**, vì nó đã có đủ entity, repository, service, controller, API helper và 4 trang admin.

Module nên làm tiếp theo là **Product**, vì backend đã có nền:

- `Product.java`
- `ProductRepository.java`
- `ProductService.java`
- `ProductRestController.java`

Nhưng frontend Admin Product còn thiếu:

- `admin/product/list.html`
- `admin/product/create.html`
- `admin/product/update.html`
- `admin/product/detail.html`

---

## 6. Các quyết định kiến trúc quan trọng

1. **Dùng HTML tĩnh thay JSP**: giao diện đặt trong `src/main/resources/static/`, không cần controller trả view.
2. **REST API trả JSON**: controller dưới `/api/v1/**` chỉ nhận dữ liệu và trả JSON.
3. **Không tự viết resource handler cho CSS/JS/ảnh tĩnh**: Spring Boot tự serve nội dung trong `static/`.
4. **Ảnh upload nằm ngoài `static/`**: dùng `upload.directory` và map qua `/images-upload/**`.
5. **URL tĩnh phải đúng đường dẫn file thật**: ví dụ `/admin/user/list.html`, không phải `/admin/user`.
6. **Layout dùng file HTML nhỏ rồi fetch vào trang chính**: admin có `header.html` và `sidebar.html`, nhúng bằng `initAdminLayout()`.
7. **Mỗi vùng có API JS riêng**: admin dùng `admin-api.js`, client sẽ dùng `client-api.js`.
8. **Upload file dùng `multipart/form-data`**: JS dùng `FormData`, Spring dùng `@ModelAttribute` và `MultipartFile`.
9. **Security đang mở tạm thời**: `permitAll()` để dev nhanh, sau này khóa `/admin/**` theo quyền admin.

---

## 7. Các điểm lệch cần nhớ để sửa sau

1. **Product trong tài liệu cũ đã lỗi thời**

   Tài liệu cũ nói Product thiếu Repository/Service, nhưng code hiện tại đã có `ProductRepository.java`, `ProductService.java`, `ProductRestController.java`.

2. **ProductRestController chưa xử lý đủ field mới**

   `Product.java` có `code`, `active`, `category`, `createdAt`, nhưng logic update hiện chỉ cập nhật các field cơ bản như name, price, desc, quantity, sold, factory, target.

3. **Upload folder đang lệch chữ hoa/thường**

   Backend đang gọi `UploadService` với folder `Avatar` và `Product`, trong khi helper frontend tạo URL `/images-upload/avatar/...` và `/images-upload/product/...`. Trên Windows thường vẫn chạy vì không phân biệt hoa/thường, nhưng nên thống nhất để tránh lỗi khi chuyển môi trường.

4. **Client còn rất sơ khai**

   `client/home.html` và `client-api.js` hiện chưa có nội dung đáng kể, nên phần client shop cần được thiết kế sau module Product Admin.

5. **JSP dependency vẫn còn trong `pom.xml`**

   `tomcat-embed-jasper` và JSTL vẫn còn. Vì dự án đã chuyển sang HTML tĩnh, có thể dọn sau khi chắc chắn không còn JSP.

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
- API users: `http://localhost:8080/api/v1/users`
- API roles: `http://localhost:8080/api/v1/roles`
- API products: `http://localhost:8080/api/v1/products`

---

## 9. Những thay đổi Domain đã triển khai

Đã cập nhật trước tiên ở lớp Domain để chuẩn bị cho Giai đoạn 1. Đây là bước đặt lại nền nghiệp vụ trước khi làm API và giao diện.

### Product

Đã bổ sung các field cấu hình laptop để sau này hiển thị chi tiết và lọc sản phẩm:

- `cpu`
- `ram`
- `storage`
- `gpu`
- `screen`
- `os`
- `weight`
- `warrantyMonths`

Ý nghĩa nghiệp vụ:

- `Product` không chỉ là một món hàng chung chung nữa, mà bắt đầu mô tả đúng một chiếc laptop.
- Các field `cpu`, `ram`, `storage`, `gpu`, `screen` sẽ dùng cho lọc sản phẩm.
- `os`, `weight`, `warrantyMonths` giúp trang chi tiết sản phẩm chuyên nghiệp hơn.
- Các field cũ như `factory`, `target`, `category`, `price`, `active`, `sold`, `createdAt` vẫn giữ vai trò quan trọng cho lọc/sắp xếp.

### Category

Đã bổ sung:

- `slug`
- `description`

Ý nghĩa nghiệp vụ:

- `Category` được định hướng là nhóm nhu cầu/phân khúc laptop, ví dụ `Laptop Gaming`, `Laptop Văn phòng`, `Laptop Sinh viên`, `Laptop Đồ họa`, `Laptop Mỏng nhẹ`.
- `slug` dùng cho URL hoặc query lọc, ví dụ `laptop-gaming`, `laptop-van-phong`.
- `description` dùng để mô tả ngắn nhóm sản phẩm ngoài trang client hoặc admin.
- Hãng sản xuất vẫn nên để ở `Product.factory`, không trộn vào `Category`.

### OrderDetail

Đã bổ sung các field snapshot sản phẩm tại thời điểm mua:

- `productCode`
- `productName`
- `productImage`

Ý nghĩa nghiệp vụ:

- `price` đã lưu giá tại thời điểm mua.
- `productCode`, `productName`, `productImage` giúp đơn hàng cũ vẫn hiển thị đúng sản phẩm khách đã mua, kể cả khi admin đổi tên, đổi ảnh hoặc ẩn sản phẩm sau này.
- Quan hệ `Product product` vẫn giữ lại để biết dòng đơn hàng liên kết với sản phẩm nào trong hệ thống.

### Xác nhận kỹ thuật

- Đã chạy `mvnw.cmd -q -DskipTests compile` sau khi sửa Domain.
- Kết quả compile thành công.
- Chưa sửa API, Service, Repository hoặc giao diện trong bước này.

---

## 10. Việc cần làm tiếp

2. **Hoàn thiện Domain Order/Coupon**
   - Tạo repository/service/controller cho Order nếu bắt đầu module đơn hàng.
   - Tạo giao diện list, create, detail, update của Counpon

3. **Nối Client với Product API**
   - Viết `client-api.js`.
   - Dựng `client/home.html`.
   - Dựng trang chi tiết sản phẩm nếu cần.

4. **Cập nhật Dashboard**
   - Hiển thị số Product/Order thật khi backend tương ứng ổn định.

5. **Dọn dẹp cuối**
   - Gỡ dependency JSP/JSTL nếu chắc chắn không dùng nữa.
   - Khóa security `/admin/**` sau khi các module chính hoàn thiện.

---

## 11. Ghi chú xác nhận

- Dự án hiện compile được với `mvnw.cmd -q -DskipTests compile`.
- Lần cập nhật tài liệu này chỉ sửa file tổng quan, không sửa logic code.
- Các thay đổi Domain chưa commit hiện tại được xem là ý định mới của bạn và cần được giữ nguyên khi code tiếp.
