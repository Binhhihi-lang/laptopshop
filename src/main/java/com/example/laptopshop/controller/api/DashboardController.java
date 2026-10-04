package com.example.laptopshop.controller.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Dashboard.DashboardRange;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.DashboardStats;
import com.example.laptopshop.service.DashboardExcelService;
import com.example.laptopshop.service.DashboardService;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardController {

    DashboardService dashboardService;
    DashboardExcelService dashboardExcelService;

    /**
     * Số liệu tổng hợp cho Bảng điều khiển.
     *
     * <p>
     * {@code range} mặc định {@code LAST_30_DAYS}. Số liệu NGƯỜI DÙNG trả về
     * {@code null} nếu người gọi thiếu quyền {@code READ_USER} (STAFF).
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('READ_DASHBOARD')")
    public ApiResponse<DashboardStats> getStats(
            @RequestParam(required = false, defaultValue = "LAST_30_DAYS") DashboardRange range) {
        ApiResponse<DashboardStats> response = new ApiResponse<>();
        response.setResult(this.dashboardService.getStats(range));
        return response;
    }

    /**
     * Xuất báo cáo tổng hợp ra Excel (.xlsx) — cùng số liệu với {@link #getStats}.
     *
     * <p>
     * Chỉ ADMIN có quyền {@code READ_USER}: báo cáo tổng hợp dành cho quản trị,
     * STAFF không xuất được (nút trên FE cũng ẩn với STAFF).
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('READ_USER')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false, defaultValue = "LAST_30_DAYS") DashboardRange range) {
        byte[] file = this.dashboardExcelService.export(range);
        String filename = "bao-cao-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(file.length);
        return new ResponseEntity<>(file, headers, org.springframework.http.HttpStatus.OK);
    }
}

