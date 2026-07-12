package com.example.laptopshop.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),

    // User
    USER_NOT_FOUND(1001, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_EXISTS(1002, "Người dùng đã tồn tại", HttpStatus.BAD_REQUEST),
    INVALID_USER_DATA(1003, "Dữ liệu người dùng không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Mật khẩu phải hơn 8 ký tự", HttpStatus.BAD_REQUEST),

    // Lỗi nghiệp vụ (Business Exception) - do Service chủ động ném ra
    PRODUCT_NOT_FOUND(1001, "Không tìm thấy sản phẩm này", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS(1002, "Sản phẩm đã tồn tại", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_DATA(1003, "Dữ liệu sản phẩm không hợp lệ", HttpStatus.BAD_REQUEST),

    // Lỗi phiếu giảm giá
    COUPON_NOT_FOUND(1002, "Không tìm thấy mã giảm giá", HttpStatus.NOT_FOUND),
    COUPON_EXPIRED(1003, "Mã giảm giá đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    COUPON_OUT_OF_STOCK(1004, "Mã giảm giá đã hết lượt sử dụng", HttpStatus.BAD_REQUEST);

    private int code;
    private String message;
    private HttpStatus httpStatus;

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