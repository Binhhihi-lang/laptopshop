package com.example.laptopshop.dto.request.Voucher;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class VoucherBulkDeleteRequest {

    @NotEmpty(message = "INVALID_VOUCHER_DATA")
    private List<String> ids;

}
