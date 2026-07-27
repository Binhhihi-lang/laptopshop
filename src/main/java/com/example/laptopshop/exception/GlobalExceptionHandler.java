package com.example.laptopshop.exception;

import com.example.laptopshop.dto.response.ApiResponse;

import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
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

    // bắt lỗi Validate form (khi dùng @Valid / @NotBlank trong DTO)
    // Ví dụ: Người dùng để trống tên sản phẩm, giá tiền bị âm...
    // Cho phép bắt cả 2 loại Exception validation
    @ExceptionHandler(value = { MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        String errorMessage = "INVALID_KEY";
        Map<String, Object> attributes = null; // Map để chứa các thuộc tính (như min, max...) từ annotation

        // Trích xuất thông tin lỗi từ đúng loại Exception tương ứng
        if (exception instanceof MethodArgumentNotValidException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                errorMessage = fieldError.getDefaultMessage();
                try {
                    // Lấy ra các attribute từ custom annotation
                    var constraintViolation = fieldError.unwrap(ConstraintViolation.class);
                    attributes = constraintViolation.getConstraintDescriptor().getAttributes();
                    log.info(attributes.toString());
                } catch (IllegalArgumentException e) {
                    // Catch lỗi nếu unwrap không thành công
                }
            }
        } else if (exception instanceof BindException ex) { // thuộc BindEx khi sử dụng công nghệ JSP , Themlyf
            FieldError fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                errorMessage = fieldError.getDefaultMessage();
                try {
                    var constraintViolation = fieldError.unwrap(ConstraintViolation.class);
                    attributes = constraintViolation.getConstraintDescriptor().getAttributes();
                } catch (IllegalArgumentException e) {
                }
            }
        }

        // Tạo đối tượng ErrorCode từ message key nhận được (VD: "INVALID_PASSWORD")
        ErrorCode errorCode;
        try {
            // đổi sang ErrorCode
            errorCode = ErrorCode.valueOf(errorMessage);
        } catch (IllegalArgumentException e) {
            errorCode = ErrorCode.INVALID_KEY; // Fallback nếu key không tồn tại trong Enum
        }

        // Xử lý binding biến {min} vào message
        String finalMessage = errorCode.getMessage();
        if (attributes != null && finalMessage.contains("{min}")) {
            // Lấy giá trị min ra từ danh sách attributes và thay thế vào chuỗi string
            String minValue = String.valueOf(attributes.get("min"));
            finalMessage = finalMessage.replace("{min}", minValue);
        }

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(finalMessage); // Dùng chuỗi finalMessage đã được map dữ liệu

        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

    // Bắt các lỗi ngầm định, lỗi hệ thống chưa phân loại (NullPointer, SQL,
    // Tomcat...) mã lỗi : 500
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(Exception exception) {
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());

        log.error("Uncaught Exception: ", exception);
        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getHttpStatus()).body(apiResponse);
    }

    // bắt lỗi 403 Authorization
    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(apiResponse);
    }
}