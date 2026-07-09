package com.example.laptopshop.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// CHỈ DÙNG ĐỂ DEBUG — xóa file này sau khi tìm ra nguyên nhân thật.
// Bắt riêng lỗi upload quá dung lượng, in ra TOÀN BỘ nguyên nhân gốc
// (kể cả các lớp "Caused by" bên trong) thay vì chỉ 1 dòng WARN cụt lủn.
@RestControllerAdvice
public class DebugUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // In full stack trace ra terminal, kéo xuống xem toàn bộ
        ex.printStackTrace();

        // Trả luôn thông tin ra response để khỏi phải lục terminal
        StringBuilder sb = new StringBuilder();
        sb.append("message: ").append(ex.getMessage()).append("\n");
        Throwable cause = ex.getCause();
        int level = 1;
        while (cause != null) {
            sb.append("cause[").append(level).append("]: ")
                    .append(cause.getClass().getName())
                    .append(" - ").append(cause.getMessage()).append("\n");
            cause = cause.getCause();
            level++;
        }

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(sb.toString());
    }
}
