package com.example.laptopshop.dto.request.Voucher;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/** Admin phát voucher đích danh cho một nhóm khách. */
@Getter
@Setter
public class VoucherAssignRequest {

    @NotBlank(message = "VOUCHER_NOT_USABLE")
    private String voucherId;

    @NotEmpty(message = "Vui lòng chọn ít nhất một khách")
    private List<String> userIds;
}
