package com.example.laptopshop.controller.api;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.DashboardStats;
import com.example.laptopshop.service.DashboardService;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardController {

    DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('READ_DASHBOARD')")
    public ApiResponse<DashboardStats> getStats() {
        ApiResponse<DashboardStats> response = new ApiResponse<>();
        response.setResult(this.dashboardService.getStats());
        return response;
    }
}
