package com.example.laptopshop.controller.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.dto.request.Order.OrderBulkStatusRequest;
import com.example.laptopshop.dto.request.Order.OrderStatusUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderDetailResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderResponse;
import com.example.laptopshop.dto.response.Order.OrderStatsResponse;
import com.example.laptopshop.service.OrderService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Quản lý đơn hàng cho admin/staff.
 * Đường dẫn: /api/v1/admin/orders
 *
 * Không có endpoint xóa: đơn hàng là bản ghi giao dịch, chỉ đổi trạng thái
 * (hủy đơn là CANCELLED) chứ không xóa khỏi hệ thống.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderRestController {

    OrderService orderService;

    /** Danh sách đơn, lọc theo trạng thái / thanh toán / khoảng ngày / từ khóa. */
    @GetMapping
    @PreAuthorize("hasAuthority('READ_ORDER')")
    public ApiResponse<Page<AdminOrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 10, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        ApiResponse<Page<AdminOrderResponse>> response = new ApiResponse<>();
        response.setResult(this.orderService.getOrdersForAdmin(
                status,
                paymentStatus,
                keyword,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.atTime(23, 59, 59),
                pageable));
        return response;
    }

    /** Thẻ thống kê đầu trang — đặt trước /{id} để không bị nuốt bởi path variable. */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('READ_ORDER')")
    public ApiResponse<OrderStatsResponse> getStats() {
        ApiResponse<OrderStatsResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.getOrderStats());
        return response;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_ORDER')")
    public ApiResponse<AdminOrderDetailResponse> getOrderDetail(@PathVariable String id) {
        ApiResponse<AdminOrderDetailResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.getOrderDetailForAdmin(id));
        return response;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('UPDATE_ORDER')")
    public ApiResponse<AdminOrderDetailResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        ApiResponse<AdminOrderDetailResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.updateOrderStatus(id, request.getStatus()));
        return response;
    }

    /** Đổi trạng thái nhiều đơn; đơn có bước chuyển không hợp lệ sẽ bị bỏ qua. */
    @PostMapping("/bulk-status")
    @PreAuthorize("hasAuthority('UPDATE_ORDER')")
    public ApiResponse<Integer> bulkUpdateStatus(@Valid @RequestBody OrderBulkStatusRequest request) {
        ApiResponse<Integer> response = new ApiResponse<>();
        int updated = this.orderService.bulkUpdateOrderStatus(request);
        response.setResult(updated);
        response.setMessage("Đã cập nhật " + updated + "/" + request.getIds().size() + " đơn hàng");
        return response;
    }
}
