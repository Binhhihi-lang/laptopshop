package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Coupon;
import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.dto.response.Coupon.CouponResponse;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    // Bỏ qua các field cần xử lý riêng trong Service:
    // - id: do JPA tự sinh
    // - code: cần trim().toUpperCase()
    // - usageLimit: cần normalize (null/âm -> 0), không map thẳng
    // - usedCount: luôn = 0 khi tạo mới, không cho tự set
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "usageLimit", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    @Mapping(target = "active", ignore = true)
    Coupon toEntity(CouponCreationRequest request);

    // @MappingTarget: đổ dữ liệu mới từ DTO ĐÈ LÊN Entity cũ đã có sẵn.
    // "active" KHÔNG bị ignore vì gán thẳng (request.isActive() ->
    // entity.setActive())
    // không có xử lý đặc biệt nào, để MapStruct tự map là an toàn.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "usageLimit", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    void updateEntity(CouponUpdateRequest request, @MappingTarget Coupon entity);

    CouponResponse toResponse(Coupon coupon);

    List<CouponResponse> toResponseList(List<Coupon> coupons);
}
