package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.VoucherScope;
import com.example.laptopshop.dto.request.Voucher.VoucherCreationRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherUpdateRequest;
import com.example.laptopshop.dto.response.Voucher.VoucherResponse;

@Mapper(componentModel = "spring")
public interface VoucherMapper {

    // Bỏ qua các field cần xử lý riêng trong Service:
    // - id: do JPA tự sinh
    // - code: cần trim().toUpperCase()
    // - usageLimit: cần normalize (null/âm -> 0), không map thẳng
    // - usedCount: luôn = 0 khi tạo mới, không cho tự set
    // - scopeValues: DTO là List<String>, entity là List<VoucherScope> — service dựng
    // - scopes: quan hệ con, service tự set
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "usageLimit", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "scopes", ignore = true)
    @Mapping(target = "scopeValue", ignore = true) // cột cũ, đã thay bằng bảng voucher_scopes
    Voucher toEntity(VoucherCreationRequest request);

    // @MappingTarget: đổ dữ liệu mới từ DTO ĐÈ LÊN Entity cũ đã có sẵn.
    // "active" KHÔNG bị ignore vì gán thẳng (request.isActive() ->
    // entity.setActive())
    // không có xử lý đặc biệt nào, để MapStruct tự map là an toàn.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "usageLimit", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "scopes", ignore = true)
    @Mapping(target = "scopeValue", ignore = true) // cột cũ, đã thay bằng bảng voucher_scopes
    void updateEntity(VoucherUpdateRequest request, @MappingTarget Voucher entity);

    /**
     * Map sang response rồi tự đổ {@code scopeValues} từ quan hệ con — MapStruct
     * không tự chuyển được {@code List<VoucherScope>} thành {@code List<String>}.
     */
    default VoucherResponse toResponse(Voucher voucher) {
        if (voucher == null) {
            return null;
        }
        VoucherResponse response = toResponseBase(voucher);
        response.setScopeValues(voucher.getScopes() == null
                ? List.of()
                : voucher.getScopes().stream().map(VoucherScope::getTargetValue).toList());
        return response;
    }

    @Mapping(target = "scopeValues", ignore = true)
    VoucherResponse toResponseBase(Voucher voucher);

    default List<VoucherResponse> toResponseList(List<Voucher> vouchers) {
        return vouchers == null ? List.of() : vouchers.stream().map(this::toResponse).toList();
    }
}
