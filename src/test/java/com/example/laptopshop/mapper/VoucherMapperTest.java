package com.example.laptopshop.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.dto.request.Voucher.VoucherCreationRequest;
import com.example.laptopshop.dto.request.Voucher.VoucherUpdateRequest;
import com.example.laptopshop.dto.response.Voucher.VoucherResponse;

/**
 * Test MAPPER THẬT (không mock) — bắt lỗi mất trường khi map entity ↔ DTO.
 *
 * <p>
 * Bug thật: sửa voucher "giảm thẳng 500k" thì DB có {@code discount_amount = 500000}
 * nhưng API trả về {@code discountAmount = null}. Nghi mapper MapStruct không map
 * trường này. Test dùng đúng implementation MapStruct sinh ra, không mock.
 */
class VoucherMapperTest {

    private final VoucherMapper mapper = Mappers.getMapper(VoucherMapper.class);

    @Test
    @DisplayName("toEntity: discountAmount từ request phải vào entity (không được null)")
    void toEntity_giuDiscountAmount() {
        VoucherCreationRequest req = new VoucherCreationRequest();
        req.setCode("GIAM500K");
        req.setDiscountAmount(500_000L);

        Voucher entity = mapper.toEntity(req);

        assertEquals(500_000L, entity.getDiscountAmount(),
                "MapStruct phải map discountAmount, không được để null");
        assertNull(entity.getDiscountPercent());
    }

    @Test
    @DisplayName("toEntity: discountPercent từ request phải vào entity")
    void toEntity_giuDiscountPercent() {
        VoucherCreationRequest req = new VoucherCreationRequest();
        req.setCode("GIAM10PT");
        req.setDiscountPercent(10);

        Voucher entity = mapper.toEntity(req);

        assertEquals(10, entity.getDiscountPercent());
        assertNull(entity.getDiscountAmount());
    }

    @Test
    @DisplayName("updateEntity: đổi từ percent sang amount phải GHI ĐÈ, không giữ giá trị cũ")
    void updateEntity_doiPercentSangAmount() {
        Voucher entity = new Voucher();
        entity.setCode("V1");
        entity.setDiscountPercent(90); // giá trị cũ

        VoucherUpdateRequest req = new VoucherUpdateRequest();
        req.setCode("V1");
        req.setDiscountAmount(500_000L);
        req.setDiscountPercent(null); // FE gửi null cho nhánh không dùng

        mapper.updateEntity(req, entity);

        assertEquals(500_000L, entity.getDiscountAmount());
        // ĐÂY là chỗ dễ hỏng: nếu MapStruct bỏ qua null thì percent cũ = 90 vẫn còn
        // → calculateDiscount thấy discountPercent trước → giảm 90% (đúng 90k trên
        // đơn 100k) dù admin đã đổi sang "giảm thẳng 500k".
        assertNull(entity.getDiscountPercent(),
                "Đổi sang kiểu số tiền thì percent cũ PHẢI bị xoá");
    }

    @Test
    @DisplayName("toResponse: discountAmount từ entity phải ra DTO (không được null)")
    void toResponse_giuDiscountAmount() {
        Voucher entity = new Voucher();
        entity.setCode("GIAM500K");
        entity.setDiscountAmount(500_000L);

        VoucherResponse res = mapper.toResponse(entity);

        assertEquals(500_000L, res.getDiscountAmount(),
                "Mapper phải trả discountAmount ra response");
    }
}
