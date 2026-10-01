package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.UserVoucher;
import com.example.laptopshop.domain.UserVoucherStatus;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, String> {

    /** Ví của khách, lọc theo trạng thái — trang /voucher-wallet (D16). */
    List<UserVoucher> findByUserIdAndStatusOrderByAcquiredAtDesc(String userId, UserVoucherStatus status);

    /** Cả ví, mới nhất trước. */
    List<UserVoucher> findByUserIdOrderByAcquiredAtDesc(String userId);

    /** Voucher của ĐÚNG khách này — chặn dùng voucher người khác (4201). */
    Optional<UserVoucher> findByIdAndUserId(String id, String userId);

    /** Đã claim voucher này chưa (R17) — kiểm tra sớm để báo lỗi thân thiện. */
    boolean existsByUserIdAndVoucherId(String userId, String voucherId);

    /** Nguồn đếm `perUserLimit` cho voucher lấy từ ví (D15). */
    long countByUserIdAndVoucherId(String userId, String voucherId);

    /** Tổng voucher đã PHÁT RA của một voucher — cơ sở chặn hết lượt nhận. */
    long countByVoucherId(String voucherId);

    /**
     * Voucher đang gắn với một đơn — hủy đơn thì hoàn lại (D12).
     *
     * <p>
     * Phải viết {@code @Query} tường minh: dẫn xuất tên {@code findByOrderId} làm
     * Spring Data so {@code order} (entity) với tham số String → Hibernate ném
     * "Given entity is not associated with the persistence context".
     */
    @Query("SELECT v FROM UserVoucher v WHERE v.order.id = :orderId")
    Optional<UserVoucher> findByOrderId(@Param("orderId") String orderId);

    /** Job dọn: voucher quá hạn mà còn AVAILABLE → chuyển EXPIRED. */
    List<UserVoucher> findByStatusAndExpiresAtBefore(UserVoucherStatus status, LocalDateTime now);

    /**
     * Danh sách khách đã nhận một voucher — trang chi tiết voucher ở admin.
     *
     * <p>
     * {@code JOIN FETCH v.user} để lấy luôn tên/email trong 1 query; không fetch
     * thì N+1 khi render bảng.
     */
    @Query("SELECT v FROM UserVoucher v JOIN FETCH v.user WHERE v.voucher.id = :voucherId ORDER BY v.acquiredAt DESC")
    List<UserVoucher> findHoldersByVoucherId(@Param("voucherId") String voucherId);
}
