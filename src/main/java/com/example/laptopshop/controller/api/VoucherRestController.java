package com.example.laptopshop.controller.api;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Voucher.VoucherAssignRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherCreationRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherUpdateRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherBulkDeleteRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherBulkStatusRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Voucher.VoucherResponse;
import com.example.laptopshop.dto.response.UserVoucher.VoucherHolderResponse;
import com.example.laptopshop.service.VoucherService;
import com.example.laptopshop.service.VoucherWalletService;

import jakarta.validation.Valid;
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin")
public class VoucherRestController {

    VoucherService voucherService;
    VoucherWalletService voucherWalletService;

    @GetMapping("/vouchers")
    @PreAuthorize("hasAuthority('READ_VOUCHER')")
    public ApiResponse<List<VoucherResponse>> getAllVouchers() {
        List<VoucherResponse> vouchers = this.voucherService.getAllVouchers();
        ApiResponse<List<VoucherResponse>> response = new ApiResponse<>();
        response.setResult(vouchers);
        return response;
    }

    @GetMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('READ_VOUCHER')")
    public ApiResponse<VoucherResponse> getVoucherById(@PathVariable String id) {
        VoucherResponse voucher = this.voucherService.getVoucherResponseById(id);
        ApiResponse<VoucherResponse> response = new ApiResponse<>();
        response.setResult(voucher);
        return response;
    }

    // Voucher có ảnh nên nhận dữ liệu dạng form-data qua @ModelAttribute (giống
    // Category/Product) để hỗ trợ upload ảnh. Toàn bộ map/validate/xử lý ảnh nằm ở Service.
    @PostMapping("/vouchers")
    @PreAuthorize("hasAuthority('CREATE_VOUCHER')")
    public ApiResponse<VoucherResponse> createVoucher(@Valid @ModelAttribute VoucherCreationRequest request) {
        VoucherResponse created = this.voucherService.createVoucher(request);
        ApiResponse<VoucherResponse> response = new ApiResponse<>();
        response.setResult(created);
        return response;
    }

    @PutMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('UPDATE_VOUCHER')")
    public ApiResponse<VoucherResponse> updateVoucher(@PathVariable String id,
            @Valid @ModelAttribute VoucherUpdateRequest request) {
        VoucherResponse updated = this.voucherService.updateVoucher(id, request);
        ApiResponse<VoucherResponse> response = new ApiResponse<>();
        response.setResult(updated);
        return response;
    }

    @DeleteMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('DELETE_VOUCHER')")
    public ApiResponse<Void> deleteVoucher(@PathVariable String id) {
        this.voucherService.deleteVoucher(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

    // Xóa hàng loạt voucher theo danh sách id (body JSON { ids: [...] }).
    // Xóa ảnh + xóa mềm nằm trong 1 transaction ở VoucherService.deleteVouchersByIds().
    @PostMapping("/vouchers/bulk-delete")
    @PreAuthorize("hasAuthority('DELETE_VOUCHER')")
    public ApiResponse<Void> deleteVouchers(@Valid @RequestBody VoucherBulkDeleteRequest request) {
        this.voucherService.deleteVouchersByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

    // Kích hoạt/khóa hàng loạt voucher (body JSON { ids: [...], active: true/false })
    @PatchMapping("/vouchers/bulk-status")
    @PreAuthorize("hasAuthority('UPDATE_VOUCHER')")
    public ApiResponse<Void> updateVouchersActive(@Valid @RequestBody VoucherBulkStatusRequest request) {
        this.voucherService.updateVouchersActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

    /**
     * Phát voucher đích danh cho một nhóm khách. Trả về số voucher thực sự phát
     * thêm — khách đã có sẵn thì bỏ qua, không tính.
     */
    @PostMapping("/vouchers/assign")
    @PreAuthorize("hasAuthority('UPDATE_VOUCHER')")
    public ApiResponse<Integer> assignVoucher(@Valid @RequestBody VoucherAssignRequest request) {
        int issued = this.voucherWalletService.assignToUsers(request.getVoucherId(), request.getUserIds());
        ApiResponse<Integer> response = new ApiResponse<>();
        response.setResult(issued);
        return response;
    }

    /** Danh sách khách đã nhận voucher này — bảng "Ví voucher" ở trang chi tiết. */
    @GetMapping("/vouchers/{id}/holders")
    @PreAuthorize("hasAuthority('READ_VOUCHER')")
    public ApiResponse<List<VoucherHolderResponse>> getVoucherHolders(@PathVariable String id) {
        ApiResponse<List<VoucherHolderResponse>> response = new ApiResponse<>();
        response.setResult(this.voucherWalletService.getHolders(id));
        return response;
    }

}
