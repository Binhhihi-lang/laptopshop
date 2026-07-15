package com.example.laptopshop.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // === SYSTEM ERRORS (9000 - 9999) ===
    INVALID_KEY(9002, "Mã lỗi (Key) cấu hình không hợp lệ", HttpStatus.BAD_REQUEST),
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống không xác định!", HttpStatus.INTERNAL_SERVER_ERROR),
    DB_VIOLATION(9001, "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc cơ sở dữ liệu!", HttpStatus.BAD_REQUEST),
    

    // === USER MODULE (1000 - 1999) ===
    USER_NOT_FOUND(1001, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_EXISTS(1002, "Người dùng (Email) đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_USER_DATA(1003, "Dữ liệu người dùng không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Mật khẩu phải có ít nhất 8 ký tự", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1005, "Đăng nhập thất bại, sai tài khoản hoặc mật khẩu", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    USER_EMAIL_EMPTY(1007, "Email người dùng không được để trống", HttpStatus.BAD_REQUEST),
    USER_EMAIL_ALREADY_EXISTS(1008, "Email người dùng đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(1009, "Email người dùng không hợp lệ", HttpStatus.BAD_REQUEST),
    USER_PASSWORD_EMPTY(1010, "Mật khẩu người dùng không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1011, "Không tìm thấy quyền người dùng", HttpStatus.NOT_FOUND),

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

    // === COUPON MODULE (4000 - 4999) ===
    COUPON_NOT_FOUND(4001, "Không tìm thấy mã giảm giá", HttpStatus.NOT_FOUND),
    COUPON_ALREADY_EXISTS(4002, "Mã giảm giá này đã tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED(4003, "Mã giảm giá đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    COUPON_OUT_OF_STOCK(4004, "Mã giảm giá đã hết lượt sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_COUPON_CONFIG(4005, "Cấu hình giảm giá không hợp lệ (Chỉ chọn Phần trăm hoặc Số tiền)",
            HttpStatus.BAD_REQUEST),
    COUPON_CODE_REQUIRED(4008, "Mã giảm giá không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_PERCENT(4006, "Phần trăm giảm giá phải nằm trong khoảng 1-100", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_AMOUNT(4007, "Số tiền giảm giá phải lớn hơn 0", HttpStatus.BAD_REQUEST),

    // === ORDER & CART MODULE (5000 - 5999 )
    CART_ITEM_NOT_FOUND(5001, "Không tìm thấy sản phẩm trong giỏ hàng", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(5002, "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS(5003, "Trạng thái đơn hàng không hợp lệ để cập nhật", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}