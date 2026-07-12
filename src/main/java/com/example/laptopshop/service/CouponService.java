package com.example.laptopshop.service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.Coupon;
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
            throw new NoSuchElementException("Không tìm thấy mã giảm giá với id = " + id);
        }
        return coupon;
    }

    // Tạo mới coupon, kiểm tra trùng code, validate discountPercent/discountAmount
    public Coupon createCoupon(Coupon coupon) {
        validateCode(coupon.getCode());
        String normalizedCode = coupon.getCode().trim().toUpperCase();

        // Kiểm tra trùng code (không phân biệt hoa thường)
        if (this.couponRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Mã coupon '" + normalizedCode + "' đã tồn tại.");
        }

        validateDiscountValue(coupon.getDiscountPercent(), coupon.getDiscountAmount());

        coupon.setCode(normalizedCode);
        coupon.setUsageLimit(coupon.getUsageLimit() == null || coupon.getUsageLimit() < 0 ? 0 : coupon.getUsageLimit());
        // Coupon mới tạo luôn bắt đầu từ 0 lượt đã dùng, không cho client tự set
        coupon.setUsedCount(0);

        return this.couponRepository.save(coupon);
    }

    // Cập nhật thông tin coupon theo id
    public Coupon updateCoupon(long id, Coupon payload) {
        Coupon coupon = getCouponById(id);

        // Kiểm tra nếu code mới khác code cũ, thì mới kiểm tra trùng lặp
        if (payload.getCode() != null && !payload.getCode().isBlank()) {
            String newCode = payload.getCode().trim().toUpperCase();
            boolean isDifferentCode = !newCode.equalsIgnoreCase(coupon.getCode());
            if (isDifferentCode && this.couponRepository.existsByCodeIgnoreCase(newCode)) {
                throw new IllegalArgumentException("Mã coupon '" + newCode + "' đã tồn tại.");
            }
            coupon.setCode(newCode);
        }

        // Form update luôn gửi lên cả 2 trường discountPercent/discountAmount, chỉ 1
        // trong 2 có giá trị tùy theo loại giảm giá admin chọn -> validate rồi set
        // đè cả 2 để đảm bảo không còn giữ lại giá trị cũ của loại kia.
        validateDiscountValue(payload.getDiscountPercent(), payload.getDiscountAmount());
        coupon.setDiscountPercent(payload.getDiscountPercent());
        coupon.setDiscountAmount(payload.getDiscountAmount());

        if (payload.getExpiryDate() != null) {
            coupon.setExpiryDate(payload.getExpiryDate());
        }

        if (payload.getUsageLimit() != null && payload.getUsageLimit() >= 0) {
            coupon.setUsageLimit(payload.getUsageLimit());
        }

        // usedCount KHÔNG cho cập nhật thủ công qua form update, chỉ hệ thống tự tăng
        // khi coupon được áp dụng vào đơn hàng
        coupon.setActive(payload.isActive());

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

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Mã coupon không được để trống.");
        }
    }

    // Coupon chỉ được chọn đúng 1 trong 2 hình thức giảm giá: theo % hoặc theo
    // số tiền cố định. Không được để trống cả 2, cũng không được điền cả 2.
    private void validateDiscountValue(Integer discountPercent, Long discountAmount) {
        boolean hasPercent = discountPercent != null;
        boolean hasAmount = discountAmount != null;

        if (hasPercent == hasAmount) {
            throw new IllegalArgumentException(
                    "Chỉ được chọn 1 trong 2 hình thức giảm giá: theo phần trăm hoặc theo số tiền cố định.");
        }

        if (hasPercent && (discountPercent < 1 || discountPercent > 100)) {
            throw new IllegalArgumentException("Phần trăm giảm giá phải nằm trong khoảng 1-100.");
        }

        if (hasAmount && discountAmount <= 0) {
            throw new IllegalArgumentException("Số tiền giảm giá phải lớn hơn 0.");
        }
    }
}