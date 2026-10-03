package com.example.laptopshop.exception;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public class AppException extends RuntimeException {

    ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // sẽ chữa sẵn message lỗi của ErrorCode
        this.errorCode = errorCode;
    }

    /**
     * Biến thể kèm message RIÊNG (ghi đè message của ErrorCode) — dùng khi cần nói
     * rõ ngữ cảnh cụ thể, vd "Kho phiên của \"Dell XPS\" vượt tồn kho 90".
     * HTTP status vẫn lấy theo {@code errorCode}.
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}