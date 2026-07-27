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

}