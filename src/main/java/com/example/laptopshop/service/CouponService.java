package com.example.laptopshop.service;

import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.Coupon;
import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.dto.response.Coupon.CouponResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.CouponMapper;
import com.example.laptopshop.repository.CouponRepository;
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class CouponService {

    CouponRepository couponRepository;
    CouponMapper couponMapper;


    public List<CouponResponse> getAllCoupons() {
        List<Coupon> couList = this.couponRepository.findAll();
        return this.couponMapper.toResponseList(couList);
    }

    public Coupon getCouponById(String id) {
        return this.couponRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_FOUND));
    }

    public CouponResponse getCouponResponseById(String id) {
        Coupon coupon = getCouponById(id);
        return this.couponMapper.toResponse(coupon);
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity rồi lưu DB.
    // Controller không còn hứng trực tiếp bằng Entity Coupon nữa, giống cách làm
    // với User/Product/Category.
    public CouponResponse createCoupon(CouponCreationRequest request) {
        validateCode(request.getCode(), null);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());

        // Map các field thuần (discountPercent, discountAmount, expiryDate) từ DTO
        // sang Entity qua MapStruct. code/usageLimit/usedCount KHÔNG được map ở
        // đây (đã ignore trong CouponMapper) vì cần xử lý riêng bên dưới.
        Coupon coupon = this.couponMapper.toEntity(request);
        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());

        // Coupon mới tạo luôn bắt đầu từ 0 lượt đã dùng, không cho client tự set
        coupon.setUsedCount(0);
        Coupon couponSaved = this.couponRepository.save(coupon);
        return this.couponMapper.toResponse(couponSaved);
    }

    // Cập nhật thông tin coupon theo id
    public CouponResponse updateCoupon(String id, CouponUpdateRequest request) {
        Coupon coupon = getCouponById(id);

        validateCode(request.getCode(), id);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());

        // Đổ các field thuần (discountPercent, discountAmount, expiryDate, active)
        // từ DTO đè lên Entity cũ qua MapStruct (@MappingTarget), rồi set riêng
        // code/usageLimit bên dưới
        this.couponMapper.updateEntity(request, coupon);
        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());

        // usedCount KHÔNG cho cập nhật thủ công qua form update, chỉ hệ thống tự tăng
        // khi coupon được áp dụng vào đơn hàng
        Coupon couponUpdated = this.couponRepository.save(coupon);
        return this.couponMapper.toResponse(couponUpdated);
    }

    // Xóa coupon theo id
    public void deleteCoupon(String id) {
        Coupon coupon = getCouponById(id);
        this.couponRepository.delete(coupon);
    }

    /**
     * Kiểm tra 1 coupon còn dùng được không: đang active, chưa hết hạn, chưa vượt
     * usageLimit.
     * Có thể gọi hàm này sau này khi xử lý Order để áp mã giảm giá.
     */
    public boolean isCouponUsable(Coupon coupon) {
        if (coupon == null || !coupon.isActive())
            return false;
        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().toLocalDate().isBefore(LocalDate.now()))
            return false;
        if (coupon.getUsageLimit() != null && coupon.getUsageLimit() > 0
                && coupon.getUsedCount() != null && coupon.getUsedCount() >= coupon.getUsageLimit())
            return false;
        return true;
    }

    /**
     * Tính số tiền thực tế được giảm dựa trên tổng tiền đơn hàng.
     * Ưu tiên discountAmount (giảm trực tiếp) nếu có, ngược lại tính theo
     * discountPercent. Số tiền giảm không bao giờ vượt quá tổng tiền đơn hàng.
     * Dùng lại khi xử lý Order sau này.
     */
    public long calculateDiscount(Coupon coupon, long orderTotal) {
        if (coupon == null || orderTotal <= 0)
            return 0;

        if (coupon.getDiscountAmount() != null && coupon.getDiscountAmount() > 0) {
            return Math.min(coupon.getDiscountAmount(), orderTotal);
        }

        if (coupon.getDiscountPercent() != null && coupon.getDiscountPercent() > 0) {
            long amount = orderTotal * coupon.getDiscountPercent() / 100;
            return Math.min(amount, orderTotal);
        }

        return 0;
    }

    // Validate code + kiểm tra trùng lặp, dùng chung cho cả create và update
    // (currentId = id hiện tại, loại trừ chính nó khỏi kiểm tra
    // trùng).
    private void validateCode(String code, String currentId) {

        String normalized = code.trim();
        boolean exists = currentId == null
                ? this.couponRepository.existsByCodeIgnoreCase(normalized)
                : this.couponRepository.existsByCodeIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.COUPON_ALREADY_EXISTS);
        }
    }

    // Coupon chỉ được chọn đúng 1 trong 2 hình thức giảm giá: theo % hoặc theo
    // số tiền cố định. Không được để trống cả 2, cũng không được điền cả 2.
    private void validateDiscountValue(Integer discountPercent, Long discountAmount) {
        boolean hasPercent = discountPercent != null;
        boolean hasAmount = discountAmount != null;

        if (hasPercent == hasAmount) {
            throw new AppException(ErrorCode.INVALID_COUPON_CONFIG);
        }

        if (hasPercent && (discountPercent < 1 || discountPercent > 100)) {
            throw new AppException(ErrorCode.INVALID_DISCOUNT_PERCENT);
        }

        if (hasAmount && discountAmount <= 0) {
            throw new AppException(ErrorCode.INVALID_DISCOUNT_AMOUNT);
        }
    }
}
