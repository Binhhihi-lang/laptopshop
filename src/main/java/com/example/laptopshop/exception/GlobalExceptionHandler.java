package com.example.laptopshop.exception;

import com.example.laptopshop.dto.response.ApiResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
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

    // BẪY 2: Chuyên bắt lỗi Validate form (khi dùng @Valid / @NotBlank trong DTO)
    // Ví dụ: Người dùng để trống tên sản phẩm, giá tiền bị âm...
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String errorMessage = exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ErrorCode errorCode = ErrorCode.valueOf(errorMessage);

        // hàm builder() trong ApiResponse để tạo đối tượng ApiResponse
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    // BẪY 3: Chuyên bắt lỗi Database (Ví dụ: Thêm trùng mã code Coupon hoặc mã SKU
    // Laptop)
    @ExceptionHandler(value = DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSqlException(DataIntegrityViolationException exception) {
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        ErrorCode errorCode = ErrorCode.PRODUCT_ALREADY_EXISTS; // Mặc định là lỗi trùng dữ liệu

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