package com.example.laptopshop.exception;

import org.springframework.http.HttpStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public enum ErrorCode {
    // === SYSTEM ERRORS (9000 - 9999) ===
    INVALID_KEY(9002, "Mã lỗi (Key) cấu hình không hợp lệ", HttpStatus.BAD_REQUEST), // lỗi không có errorCode hoặc viết nhầm tên
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống không xác định!", HttpStatus.INTERNAL_SERVER_ERROR),
    DB_VIOLATION(9001, "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc cơ sở dữ liệu!", HttpStatus.BAD_REQUEST),


    // === USER MODULE (1000 - 1999) ===
    USER_NOT_FOUND(1001, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_EXISTS(1002, "Người dùng (Email) đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_USER_DATA(1003, "Dữ liệu người dùng không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1008, "Mật khẩu phải có ít nhất {min} ký tự", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1005, "Đăng nhập thất bại, sai tài khoản hoặc mật khẩu", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    USER_EMAIL_EMPTY(1007, "Email người dùng không được để trống", HttpStatus.BAD_REQUEST),
    USER_EMAIL_ALREADY_EXISTS(1008, "Email người dùng đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(1009, "Email người dùng không hợp lệ", HttpStatus.BAD_REQUEST),
    USER_INACTIVE(1010, "Tài khoản đã bị khóa, vui lòng liên hệ quản trị viên", HttpStatus.UNAUTHORIZED),
    USER_CANNOT_DEACTIVATE_SELF(1011, "Bạn không thể tự khóa tài khoản của chính mình", HttpStatus.FORBIDDEN),
    USER_CANNOT_DEACTIVATE_ADMIN(1012, "Không thể khóa người dùng có vai trò ADMIN", HttpStatus.FORBIDDEN),
    DEVICE_LIMIT_EXCEEDED(1013, "Bạn đã đăng nhập trên số thiết bị tối đa", HttpStatus.CONFLICT),
    DEVICE_SESSION_NOT_FOUND(1014, "Không tìm thấy thiết bị này trong danh sách đăng nhập", HttpStatus.NOT_FOUND),
    ADMIN_LOGIN_FORBIDDEN(1015, "Tài khoản này không có quyền truy cập trang quản trị", HttpStatus.FORBIDDEN),

    // === CATEGORY MODULE (2000 - 2999) ===
    CATEGORY_NAME_REQUIRED(2000, "Tên danh mục sản phẩm không được để trống", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND(2001, "Không tìm thấy danh mục", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(2002, "Tên danh mục sản phẩm đã tồn tại", HttpStatus.BAD_REQUEST),

    // === PRODUCT MODULE (3000 - 3999) ===
    PRODUCT_CODE_REQUIRED(3000, "Mã SKU sản phẩm không được để trống", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(3001, "Không tìm thấy sản phẩm này", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS(3002, "Mã SKU hoặc tên sản phẩm đã tồn tại", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_DATA(3003, "Dữ liệu sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),
    PRODUCT_OUT_OF_STOCK(3004, "Sản phẩm trong kho đã hết hàng", HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_INVALID(3005, "Giá sản phẩm phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    PRODUCT_NAME_EMPTY(3006, "Tên sản phẩm không được để trống", HttpStatus.BAD_REQUEST),
    PRODUCT_CATEGORY_REQUIRED(3007, "Danh mục sản phẩm không được để trống", HttpStatus.BAD_REQUEST),
    PRODUCT_ORIGINAL_PRICE_INVALID(3008, "Giá gốc phải lớn hơn hoặc bằng giá bán", HttpStatus.BAD_REQUEST),

    // === COUPON MODULE (4000 - 4999) ===
    COUPON_NOT_FOUND(4001, "Không tìm thấy mã giảm giá", HttpStatus.NOT_FOUND),
    COUPON_ALREADY_EXISTS(4002, "Mã giảm giá này đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED(4003, "Mã giảm giá đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    COUPON_OUT_OF_STOCK(4004, "Mã giảm giá đã hết lượt sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_CONFIG(4005, "Cấu hình giảm giá không hợp lệ (Chỉ chọn Phần trăm hoặc Số tiền)", HttpStatus.BAD_REQUEST),
    COUPON_CODE_REQUIRED(4008, "Mã giảm giá không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_DATA(4009, "Dữ liệu mã giảm giá không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_PERCENT(4006, "Phần trăm giảm giá phải nằm trong khoảng 1-100", HttpStatus.BAD_REQUEST),

    INVALID_DISCOUNT_AMOUNT(4007, "Số tiền giảm giá phải lớn hơn 0", HttpStatus.BAD_REQUEST),

    // === COUPON MODULE mở rộng v1 (4010 - 4099) ===
    COUPON_SCOPE_INVALID(4010, "Phạm vi áp dụng của mã giảm giá không hợp lệ", HttpStatus.BAD_REQUEST),
    COUPON_MIN_ORDER_NOT_MET(4011, "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này", HttpStatus.BAD_REQUEST),
    COUPON_PER_USER_LIMIT_REACHED(4012, "Bạn đã dùng hết số lượt cho phép của mã giảm giá này",
            HttpStatus.BAD_REQUEST),
    COUPON_NOT_STARTED(4013, "Mã giảm giá chưa đến thời gian sử dụng", HttpStatus.BAD_REQUEST),
    COUPON_NOT_IN_WALLET(4014, "Mã giảm giá này không có trong ví của bạn", HttpStatus.FORBIDDEN),

    // === PROMOTION MODULE (4100 - 4199) ===
    PROMOTION_NOT_FOUND(4101, "Không tìm thấy chương trình khuyến mại", HttpStatus.NOT_FOUND),
    PROMOTION_NAME_REQUIRED(4102, "Tên chương trình khuyến mại không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_CONFIG(4103, "Cấu hình khuyến mại không hợp lệ (chỉ chọn Phần trăm hoặc Số tiền)",
            HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_PERCENT(4104, "Phần trăm giảm giá phải nằm trong khoảng 1-100", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_AMOUNT(4105, "Số tiền giảm giá phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_DATE_RANGE(4106, "Thời gian kết thúc phải sau thời gian bắt đầu", HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_STATUS(4107, "Trạng thái chương trình khuyến mại không hợp lệ", HttpStatus.BAD_REQUEST),
    PROMOTION_SCOPE_REQUIRED(4108, "Phạm vi áp dụng không được để trống khi không chọn toàn bộ đơn",
            HttpStatus.BAD_REQUEST),
    INVALID_PROMOTION_SCOPE(4109, "Phạm vi áp dụng của chương trình không hợp lệ", HttpStatus.BAD_REQUEST),
    PROMOTION_OVERLAP(4110, "Khoảng thời gian bị trùng với chương trình khác cùng phạm vi",
            HttpStatus.BAD_REQUEST),
    // D19: incrementUsedCount trả 0 dòng = chương trình vừa hết lượt vì khách
    // khác chốt đơn song song. Tính tiền lại là đủ, không chặn đơn.
    PROMOTION_OUT_OF_STOCK(4111, "Chương trình khuyến mại vừa hết lượt, đơn được tính lại giá mới",
            HttpStatus.BAD_REQUEST),

    // === USER VOUCHER / VÍ VOUCHER (4200 - 4299) ===
    USER_VOUCHER_NOT_FOUND(4201, "Không tìm thấy mã giảm giá trong ví", HttpStatus.NOT_FOUND),
    USER_VOUCHER_ALREADY_CLAIMED(4202, "Bạn đã nhận mã giảm giá này rồi", HttpStatus.BAD_REQUEST),
    USER_VOUCHER_OUT_OF_STOCK(4203, "Mã giảm giá đã hết lượt nhận", HttpStatus.BAD_REQUEST),
    USER_VOUCHER_EXPIRED(4204, "Mã giảm giá đã hết hạn", HttpStatus.BAD_REQUEST),
    USER_VOUCHER_ALREADY_USED(4205, "Mã giảm giá này đã được sử dụng", HttpStatus.BAD_REQUEST),
    USER_VOUCHER_NOT_CLAIMABLE(4206, "Mã giảm giá này không thể nhận trước", HttpStatus.BAD_REQUEST),

    // === FLASH SALE (4300 - 4399) ===
    FLASH_SALE_NOT_FOUND(4301, "Không tìm thấy phiên flash sale", HttpStatus.NOT_FOUND),
    FLASH_SALE_NAME_REQUIRED(4302, "Tên phiên flash sale không được để trống", HttpStatus.BAD_REQUEST),
    // D26: flash sale mà giá cao hơn giá thường thì không phải khuyến mại.
    FLASH_PRICE_NOT_LOWER(4303, "Giá flash sale phải thấp hơn giá bán hiện tại của sản phẩm",
            HttpStatus.BAD_REQUEST),
    // Chốt chặn ở tầng DB (UK_flash_sale_items_sale_product) vẫn còn; lỗi này để
    // admin biết ngay sản phẩm nào bị trùng thay vì nhận message ràng buộc.
    FLASH_SALE_ITEM_DUPLICATE(4304, "Sản phẩm này đã có trong phiên flash sale", HttpStatus.BAD_REQUEST),
    INVALID_FLASH_SALE_DATE_RANGE(4305, "Thời gian kết thúc phiên phải sau thời gian bắt đầu",
            HttpStatus.BAD_REQUEST),
    FLASH_SALE_NO_ITEMS(4306, "Phiên flash sale phải có ít nhất một sản phẩm", HttpStatus.BAD_REQUEST),
    INVALID_FLASH_PRICE(4307, "Giá flash sale phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    INVALID_FLASH_STOCK(4308, "Số lượng flash sale phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    // D29: consumeStock trả 0 dòng = khách khác vừa chốt máy cuối cùng. Đơn KHÔNG
    // fail — dòng đó rơi về giá thường (D27), nên đây là thông báo, không phải lỗi chặn.
    FLASH_STOCK_EXHAUSTED(4309, "Sản phẩm đã bán hết số lượng dành cho flash sale", HttpStatus.BAD_REQUEST),
    // D32: perUserLimit — flash giá sốc rất dễ bị dân buôn quét.
    FLASH_PER_USER_LIMIT_REACHED(4310, "Bạn đã mua đủ số lượng tối đa cho sản phẩm flash sale này",
            HttpStatus.BAD_REQUEST),
    FLASH_SALE_PRODUCT_NOT_FOUND(4311, "Sản phẩm đưa vào phiên không tồn tại", HttpStatus.NOT_FOUND),

    // === HOME BANNER (4400 - 4499) ===
    BANNER_NOT_FOUND(4401, "Không tìm thấy banner trang chủ", HttpStatus.NOT_FOUND),
    BANNER_TITLE_REQUIRED(4402, "Tiêu đề banner không được để trống", HttpStatus.BAD_REQUEST),
    BANNER_IMAGE_REQUIRED(4403, "Banner phải có ảnh", HttpStatus.BAD_REQUEST),
    BANNER_TARGET_REQUIRED(4404, "Banner chưa chọn nơi dẫn tới", HttpStatus.BAD_REQUEST),
    // D30: banner là nơi admin dán link. Chỉ nhận đường dẫn nội bộ "/...", chặn
    // javascript: và absolute external để không thành open-redirect.
    INVALID_BANNER_TARGET_URL(4405,
            "Liên kết banner không hợp lệ (chỉ chấp nhận đường dẫn nội bộ bắt đầu bằng /)",
            HttpStatus.BAD_REQUEST),
    INVALID_BANNER_TARGET(4406, "Đối tượng của banner không tồn tại hoặc không hợp lệ", HttpStatus.BAD_REQUEST),

    // === ORDER & CART MODULE (5000 - 5999 )
    CART_ITEM_NOT_FOUND(5001, "Không tìm thấy sản phẩm trong giỏ hàng", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(5002, "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS(5003, "Trạng thái đơn hàng không hợp lệ để cập nhật", HttpStatus.BAD_REQUEST),
    CART_EMPTY(5004, "Giỏ hàng đang trống, không thể đặt hàng", HttpStatus.BAD_REQUEST),
    INVALID_CART_QUANTITY(5005, "Số lượng sản phẩm phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    INVALID_CART_DATA(5006, "Dữ liệu giỏ hàng không hợp lệ", HttpStatus.BAD_REQUEST),
    CART_QUANTITY_EXCEEDS_STOCK(5007, "Số lượng vượt quá tồn kho hiện có", HttpStatus.BAD_REQUEST),
    INVALID_RECEIVER_NAME(5008, "Họ tên người nhận không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_RECEIVER_PHONE(5009, "Số điện thoại người nhận không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_RECEIVER_ADDRESS(5010, "Địa chỉ nhận hàng không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_METHOD(5011, "Phương thức thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_TOTAL(5012, "Tổng tiền đơn hàng không hợp lệ", HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_CANCEL(5013, "Chỉ có thể hủy đơn đang chờ xử lý hoặc đã xác nhận", HttpStatus.BAD_REQUEST),
    COUPON_NOT_USABLE(5014, "Mã giảm giá không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    INVALID_RECEIVER_EMAIL(5020, "Email người nhận không hợp lệ", HttpStatus.BAD_REQUEST),

    // === PAYMENT (5021 - 5029) ===
    PAYMENT_NOT_FOUND(5021, "Không tìm thấy giao dịch thanh toán", HttpStatus.NOT_FOUND),
    PAYMENT_TOO_MANY_ATTEMPTS(5022, "Đã vượt quá số lần thanh toán cho phép của đơn này",
            HttpStatus.TOO_MANY_REQUESTS),
    ORDER_PAYMENT_EXPIRED(5023, "Đơn hàng đã quá hạn thanh toán, vui lòng đặt lại đơn mới",
            HttpStatus.BAD_REQUEST),

    // === VNPAY (5015 - 5019) ===
    VNPAY_NOT_CONFIGURED(5015, "Cổng thanh toán VNPay chưa được cấu hình", HttpStatus.SERVICE_UNAVAILABLE),
    ORDER_ALREADY_PAID(5016, "Đơn hàng đã được thanh toán", HttpStatus.BAD_REQUEST),
    ORDER_NOT_PAYABLE(5017, "Đơn hàng không thể thanh toán qua VNPay", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_CODE(5018, "Mã đơn hàng không được để trống", HttpStatus.BAD_REQUEST),
    VNPAY_INVALID_SIGNATURE(5019, "Chữ ký xác thực từ VNPay không hợp lệ", HttpStatus.BAD_REQUEST),

    // ROLE & PERMISSION (6000 - 6999)
    ROLE_NAME_EMPTY(6000, "Tên role không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_NAME_EXISTED(6001, "Tên role đã tồn tại", HttpStatus.CONFLICT),
    ROLE_PERMISSIONS_EMPTY(6002, "Danh sách quyền không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(6003, "Không tìm thấy quyền người dùng", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND(6004, "Không tìm thấy quyền", HttpStatus.NOT_FOUND),
    PERMISSION_NAME_EMPTY(6005, "Tên quyền không được để trống", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_EXISTED(6006, "Tên quyền đã tồn tại", HttpStatus.CONFLICT),
    USER_ROLES_EMPTY(6007, "Danh sách vai trò không được để trống", HttpStatus.CONFLICT),
    ROLE_BULK_EMPTY(6008, "Danh sách vai trò không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_CANNOT_DEACTIVATE(6009, "Không thể khóa hoặc vô hiệu hóa vai trò ADMIN", HttpStatus.FORBIDDEN),
//    PERMISSION_BULK_EMPTY(6010, "Danh sách quyền không được để trống", HttpStatus.BAD_REQUEST),
    PERMISSION_CANNOT_DEACTIVATE(6011, "Không thể khóa quyền hệ thống (quyền có tiền tố MANAGE_)", HttpStatus.FORBIDDEN),
    ACCESS_REVOKED(6012, "Quyền truy cập của bạn đã bị thu hồi, vui lòng đăng nhập lại", HttpStatus.FORBIDDEN),

    // Auth
    REFRESH_TOKEN_EXPIRED(7000, "Refresh token đã hết hạn.", HttpStatus.BAD_REQUEST),
    TOKEN_EMPTY(7001, "Token không được để trống.", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_NOT_FOUND(7002, "Refresh token không hợp lệ ", HttpStatus.BAD_REQUEST),
    REVOKE_TICKET_INVALID(7005, "Phiên xác thực đã hết hạn, vui lòng đăng nhập lại", HttpStatus.BAD_REQUEST),

    // Password reset (client storefront)
    PASSWORD_RESET_TOKEN_INVALID(7003, "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    PASSWORD_RESET_TOKEN_USED(7004, "Token đặt lại mật khẩu đã được sử dụng", HttpStatus.BAD_REQUEST),

    // === IMAGE UPLOAD (8000 - 8999) ===
    INVALID_IMAGE_URL(8000, "Link ảnh không hợp lệ (chỉ chấp nhận http/https)", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED(8001, "Không thể tải ảnh từ link, vui lòng kiểm tra lại URL", HttpStatus.BAD_REQUEST);


    int code;
    String message;
    HttpStatus httpStatus;
}