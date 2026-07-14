package com.example.laptopshop.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.Coupon;
import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CouponRepository;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public List<Coupon> getAllCoupons() {
        return this.couponRepository.findAll();
    }

    public Coupon getCouponById(long id) {
        Coupon coupon = this.couponRepository.findById(id);
        if (coupon == null) {
            throw new AppException(ErrorCode.COUPON_NOT_FOUND);
        }
        return coupon;
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity rồi lưu DB.
    // Controller không còn hứng trực tiếp bằng Entity Coupon nữa, giống cách làm
    // với User/Product/Category.
    public Coupon createCoupon(CouponCreationRequest request) {
        validateCode(request.getCode(), null);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());

        Coupon coupon = new Coupon();
        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setDiscountPercent(request.getDiscountPercent());
        coupon.setDiscountAmount(request.getDiscountAmount());
        coupon.setExpiryDate(request.getExpiryDate());
        coupon.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());

        // Coupon mới tạo luôn bắt đầu từ 0 lượt đã dùng, không cho client tự set
        coupon.setUsedCount(0);

        return this.couponRepository.save(coupon);
    }

    // Cập nhật thông tin coupon theo id
    public Coupon updateCoupon(long id, CouponUpdateRequest request) {
        Coupon coupon = getCouponById(id);

        validateCode(request.getCode(), id);
        validateDiscountValue(request.getDiscountPercent(), request.getDiscountAmount());

        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setDiscountPercent(request.getDiscountPercent());
        coupon.setDiscountAmount(request.getDiscountAmount());
        coupon.setExpiryDate(request.getExpiryDate());
        coupon.setUsageLimit(
                request.getUsageLimit() == null || request.getUsageLimit() < 0 ? 0 : request.getUsageLimit());
        coupon.setActive(request.isActive());

        // usedCount KHÔNG cho cập nhật thủ công qua form update, chỉ hệ thống tự tăng
        // khi coupon được áp dụng vào đơn hàng

        return this.couponRepository.save(coupon);
    }

    // Xóa coupon theo id
    public void deleteCoupon(long id) {
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

    // Validate code + kiểm tra trùng lặp, dùng chung cho cả create (currentId =
    // null) và update (currentId = id hiện tại, loại trừ chính nó khỏi kiểm tra
    // trùng).
    private void validateCode(String code, Long currentId) {
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.COUPON_CODE_REQUIRED);
        }

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