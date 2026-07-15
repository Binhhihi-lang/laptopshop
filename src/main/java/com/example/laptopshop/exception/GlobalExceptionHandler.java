package com.example.laptopshop.exception;

import com.example.laptopshop.dto.response.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Bắt các lỗi nghiệp vụ chủ động ném ra từ Service (AppException)
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        // Lấy thông tin lỗi từ Enum ErrorCode
        ErrorCode errorCode = exception.getErrorCode();

        ApiResponse<Void> apiResponse = new ApiResponse<>();

        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        // Trả về chuẩn HTTP Status (404, 400, 500...) tùy cấu hình trong Enum
        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    // Chuyên bắt lỗi Validate form (khi dùng @Valid / @NotBlank trong DTO)
    // Ví dụ: Người dùng để trống tên sản phẩm, giá tiền bị âm...
    // Cho phép bắt cả 2 loại Exception validation
    @ExceptionHandler(value = { MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        String errorMessage = "INVALID_KEY";

        // Trích xuất thông tin lỗi từ đúng loại Exception tương ứng
        if (exception instanceof MethodArgumentNotValidException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                errorMessage = fieldError.getDefaultMessage();
            }
        } else if (exception instanceof BindException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                errorMessage = fieldError.getDefaultMessage();
            }
        }

        // Tạo đối tượng ErrorCode từ message key nhận được (VD: "INVALID_EMAIL",
        // "USER_EMAIL_EMPTY")
        ErrorCode errorCode = ErrorCode.valueOf(errorMessage);

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    // Bắt các lỗi ngầm định, lỗi hệ thống chưa phân loại (NullPointer, SQL,
    // Tomcat...)
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(Exception exception) {
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());

        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getHttpStatus()).body(apiResponse);
    }
}