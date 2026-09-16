package com.example.laptopshop.exception;

import com.example.laptopshop.dto.response.DeviceLimitResponse;

import lombok.Getter;

/**
 * Ném khi user đã đủ số thiết bị tối đa mà login thêm thiết bị mới.
 *
 * <p>Khác {@link AppException} ở chỗ mang thêm payload: danh sách thiết bị đang
 * đăng nhập + revoke ticket để FE hiện dialog và cho user đá thiết bị cũ.
 * {@code GlobalExceptionHandler} có handler riêng trả payload này trong
 * {@code result}.
 */
@Getter
public class DeviceLimitExceededException extends AppException {

    private final transient DeviceLimitResponse payload;

    public DeviceLimitExceededException(DeviceLimitResponse payload) {
        super(ErrorCode.DEVICE_LIMIT_EXCEEDED);
        this.payload = payload;
    }
}
