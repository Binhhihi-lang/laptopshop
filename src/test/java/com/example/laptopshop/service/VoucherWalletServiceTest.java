package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.domain.UserVoucher;
import com.example.laptopshop.domain.UserVoucherSource;
import com.example.laptopshop.domain.UserVoucherStatus;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.repository.UserVoucherRepository;

/**
 * Test VoucherWalletService — vòng đời voucher trong ví (§3.4):
 * claim (R17), dùng, hoàn khi hủy đơn (D12), hết hạn.
 */
@ExtendWith(MockitoExtension.class)
class VoucherWalletServiceTest {

    @Mock
    private UserVoucherRepository userVoucherRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VoucherService voucherService;

    @InjectMocks
    private VoucherWalletService voucherWalletService;

    private Voucher voucher;
    private User user;

    @BeforeEach
    void setUp() {
        voucher = new Voucher();
        voucher.setId("voucher-1");
        voucher.setCode("GIAM10");
        voucher.setActive(true);
        voucher.setVoucherType(VoucherType.PUBLIC);

        user = new User();
        user.setId("u1");
    }

    private UserVoucher voucher(UserVoucherStatus status, LocalDateTime expiresAt) {
        UserVoucher v = new UserVoucher();
        v.setId("uv-1");
        v.setUser(user);
        v.setVoucher(voucher);
        v.setStatus(status);
        v.setSource(UserVoucherSource.CLAIMED);
        v.setAcquiredAt(LocalDateTime.now().minusDays(1));
        v.setExpiresAt(expiresAt);
        return v;
    }

    // ==================================================================
    // Claim (R17)
    // ==================================================================

    @Nested
    @DisplayName("Claim voucher")
    class Claim {

        @BeforeEach
        void allowUsable() {
            lenient().when(voucherService.isVoucherUsable(any(Voucher.class))).thenReturn(true);
        }

        @Test
        @DisplayName("Claim hợp lệ → tạo voucher AVAILABLE, chép hạn từ voucher")
        void claimHopLe() {
            LocalDateTime expiry = LocalDateTime.now().plusDays(30);
            voucher.setExpiryDate(expiry);
            when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher));
            when(userVoucherRepository.existsByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(false);
            when(userVoucherRepository.countByVoucherId("voucher-1")).thenReturn(0L);
            when(userRepository.findById("u1")).thenReturn(Optional.of(user));
            when(userVoucherRepository.save(any(UserVoucher.class))).thenAnswer(inv -> {
                UserVoucher v = inv.getArgument(0);
                v.setId("uv-1");
                return v;
            });

            var res = voucherWalletService.claim("u1", "voucher-1");

            assertEquals(UserVoucherStatus.AVAILABLE, res.getStatus());
            assertEquals(UserVoucherSource.CLAIMED, res.getSource());
            assertEquals(expiry, res.getExpiresAt());
        }

        @Test
        @DisplayName("Claim trùng → USER_VOUCHER_ALREADY_CLAIMED (R17)")
        void claimTrung() {
            when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher));
            when(userVoucherRepository.existsByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(true);

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.claim("u1", "voucher-1"));
            assertEquals(ErrorCode.USER_VOUCHER_ALREADY_CLAIMED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher ASSIGNED (admin phát đích danh) → không cho tự claim")
        void khongClaimDuocAssigned() {
            voucher.setVoucherType(VoucherType.ASSIGNED);
            when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.claim("u1", "voucher-1"));
            assertEquals(ErrorCode.USER_VOUCHER_NOT_CLAIMABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Đã phát đủ usageLimit → USER_VOUCHER_OUT_OF_STOCK")
        void hetLuotPhat() {
            voucher.setUsageLimit(5);
            when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher));
            when(userVoucherRepository.existsByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(false);
            when(userVoucherRepository.countByVoucherId("voucher-1")).thenReturn(5L);

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.claim("u1", "voucher-1"));
            assertEquals(ErrorCode.USER_VOUCHER_OUT_OF_STOCK, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher không tồn tại → VOUCHER_NOT_USABLE")
        void voucherKhongTonTai() {
            when(voucherRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(AppException.class, () -> voucherWalletService.claim("u1", "missing"));
        }
    }

    // ==================================================================
    // Dùng voucher
    // ==================================================================

    @Nested
    @DisplayName("Lấy voucher để dùng")
    class GetUsable {

        @Test
        @DisplayName("Voucher còn hạn → dùng được")
        void conHan() {
            UserVoucher v = voucher(UserVoucherStatus.AVAILABLE, LocalDateTime.now().plusDays(1));
            when(userVoucherRepository.findByIdAndUserId("uv-1", "u1")).thenReturn(Optional.of(v));

            assertNotNull(voucherWalletService.getUsableVoucher("u1", "uv-1"));
        }

        @Test
        @DisplayName("Voucher của khách KHÁC → USER_VOUCHER_NOT_FOUND (không lộ sự tồn tại)")
        void cuaNguoiKhac() {
            when(userVoucherRepository.findByIdAndUserId("uv-1", "u1")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.getUsableVoucher("u1", "uv-1"));
            assertEquals(ErrorCode.USER_VOUCHER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Đã dùng rồi → USER_VOUCHER_ALREADY_USED")
        void daDung() {
            UserVoucher v = voucher(UserVoucherStatus.USED, LocalDateTime.now().plusDays(1));
            when(userVoucherRepository.findByIdAndUserId("uv-1", "u1")).thenReturn(Optional.of(v));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.getUsableVoucher("u1", "uv-1"));
            assertEquals(ErrorCode.USER_VOUCHER_ALREADY_USED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Quá hạn → USER_VOUCHER_EXPIRED")
        void quaHan() {
            UserVoucher v = voucher(UserVoucherStatus.AVAILABLE, LocalDateTime.now().minusDays(1));
            when(userVoucherRepository.findByIdAndUserId("uv-1", "u1")).thenReturn(Optional.of(v));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.getUsableVoucher("u1", "uv-1"));
            assertEquals(ErrorCode.USER_VOUCHER_EXPIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("expiresAt null = trường tồn → dùng được mãi")
        void truongTon() {
            UserVoucher v = voucher(UserVoucherStatus.AVAILABLE, null);
            when(userVoucherRepository.findByIdAndUserId("uv-1", "u1")).thenReturn(Optional.of(v));

            assertNotNull(voucherWalletService.getUsableVoucher("u1", "uv-1"));
        }
    }

    @Nested
    @DisplayName("Đánh dấu đã dùng")
    class MarkUsed {

        @Test
        @DisplayName("Chuyển USED + gắn đơn + ghi usedAt")
        void danhDau() {
            UserVoucher v = voucher(UserVoucherStatus.AVAILABLE, LocalDateTime.now().plusDays(1));
            Order order = new Order();
            order.setId("order-1");

            voucherWalletService.markUsed(v, order);

            assertEquals(UserVoucherStatus.USED, v.getStatus());
            assertEquals(order, v.getOrder());
            assertNotNull(v.getUsedAt());
            verify(userVoucherRepository).save(v);
        }
    }

    // ==================================================================
    // D12 — hoàn voucher khi hủy đơn
    // ==================================================================

    @Nested
    @DisplayName("releaseOnCancel (D12)")
    class ReleaseOnCancel {

        @Test
        @DisplayName("Còn hạn → về AVAILABLE, xóa dấu đơn")
        void conHanVeAvailable() {
            UserVoucher v = voucher(UserVoucherStatus.USED, LocalDateTime.now().plusDays(1));
            v.setOrder(new Order());
            v.setUsedAt(LocalDateTime.now());

            voucherWalletService.releaseOnCancel(v);

            assertEquals(UserVoucherStatus.AVAILABLE, v.getStatus());
            assertNull(v.getOrder());
            assertNull(v.getUsedAt());
        }

        @Test
        @DisplayName("Quá hạn → EXPIRED, KHÔNG về AVAILABLE (không hồi sinh voucher chết)")
        void quaHanThanhExpired() {
            UserVoucher v = voucher(UserVoucherStatus.USED, LocalDateTime.now().minusDays(1));

            voucherWalletService.releaseOnCancel(v);

            assertEquals(UserVoucherStatus.EXPIRED, v.getStatus());
        }

        @Test
        @DisplayName("Trường tồn (null hạn) → về AVAILABLE")
        void truongTon() {
            UserVoucher v = voucher(UserVoucherStatus.USED, null);

            voucherWalletService.releaseOnCancel(v);

            assertEquals(UserVoucherStatus.AVAILABLE, v.getStatus());
        }

        @Test
        @DisplayName("Đơn không dùng voucher (null) → không làm gì, không NPE")
        void khongCoVoucher() {
            voucherWalletService.releaseOnCancel(null);

            verify(userVoucherRepository, never()).save(any());
        }
    }

    // ==================================================================
    // Job dọn hết hạn
    // ==================================================================

    @Nested
    @DisplayName("expireOverdue")
    class ExpireOverdue {

        @Test
        @DisplayName("Voucher quá hạn còn AVAILABLE → chuyển EXPIRED, trả số lượng")
        void chuyenExpired() {
            UserVoucher v1 = voucher(UserVoucherStatus.AVAILABLE, LocalDateTime.now().minusDays(1));
            UserVoucher v2 = voucher(UserVoucherStatus.AVAILABLE, LocalDateTime.now().minusDays(2));
            when(userVoucherRepository.findByStatusAndExpiresAtBefore(
                    any(UserVoucherStatus.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(v1, v2));

            int changed = voucherWalletService.expireOverdue();

            assertEquals(2, changed);
            assertEquals(UserVoucherStatus.EXPIRED, v1.getStatus());
            assertEquals(UserVoucherStatus.EXPIRED, v2.getStatus());
        }
    }

    // ==================================================================
    // Đọc ví
    // ==================================================================

    @Nested
    @DisplayName("Đọc ví")
    class ReadWallet {

        @Test
        @DisplayName("Lọc theo trạng thái → gọi đúng method repository")
        void locTheoTrangThai() {
            UserVoucher v = voucher(UserVoucherStatus.AVAILABLE, null);
            when(userVoucherRepository.findByUserIdAndStatusOrderByAcquiredAtDesc(
                    "u1", UserVoucherStatus.AVAILABLE)).thenReturn(List.of(v));

            var res = voucherWalletService.getMyVouchers("u1", UserVoucherStatus.AVAILABLE);

            assertEquals(1, res.size());
            assertEquals("GIAM10", res.get(0).getCode());
        }

        @Test
        @DisplayName("status null → trả cả ví")
        void caVi() {
            when(userVoucherRepository.findByUserIdOrderByAcquiredAtDesc("u1"))
                    .thenReturn(List.of(voucher(UserVoucherStatus.USED, null)));

            var res = voucherWalletService.getMyVouchers("u1", null);

            assertEquals(1, res.size());
            assertTrue(res.get(0).getStatus() == UserVoucherStatus.USED);
        }
    }

    // ==================================================================
    // Phát voucher đích danh (admin gán cho nhóm khách)
    // ==================================================================

    @Nested
    @DisplayName("assignToUsers — admin phát voucher")
    class Assign {

        @BeforeEach
        void allowUsable() {
            lenient().when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher));
            lenient().when(voucherService.isVoucherUsable(any(Voucher.class))).thenReturn(true);
        }

        private User other(String id) {
            User u = new User();
            u.setId(id);
            return u;
        }

        @Test
        @DisplayName("Phát cho 2 khách mới → tạo 2 voucher, source CLAIMED (voucher PUBLIC)")
        void phatChoHaiKhach() {
            when(userVoucherRepository.existsByUserIdAndVoucherId(any(), any())).thenReturn(false);
            when(userRepository.findById("u1")).thenReturn(Optional.of(user));
            when(userRepository.findById("u2")).thenReturn(Optional.of(other("u2")));

            int issued = voucherWalletService.assignToUsers("voucher-1", List.of("u1", "u2"));

            assertEquals(2, issued);
            var captor = org.mockito.ArgumentCaptor.forClass(UserVoucher.class);
            verify(userVoucherRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            // Voucher PUBLIC gán tay = khách "tự nhận" được, không phải quà tặng.
            assertTrue(captor.getAllValues().stream()
                    .allMatch(v -> v.getSource() == UserVoucherSource.CLAIMED));
            assertTrue(captor.getAllValues().stream()
                    .allMatch(v -> v.getStatus() == UserVoucherStatus.AVAILABLE));
        }

        @Test
        @DisplayName("Voucher ASSIGNED → source GIFTED (phát đích danh, không tự nhận)")
        void voucherAssignedRaGifted() {
            voucher.setVoucherType(VoucherType.ASSIGNED);
            when(userVoucherRepository.existsByUserIdAndVoucherId(any(), any())).thenReturn(false);
            when(userRepository.findById("u1")).thenReturn(Optional.of(user));

            voucherWalletService.assignToUsers("voucher-1", List.of("u1"));

            var captor = org.mockito.ArgumentCaptor.forClass(UserVoucher.class);
            verify(userVoucherRepository).save(captor.capture());
            assertEquals(UserVoucherSource.GIFTED, captor.getValue().getSource());
        }

        @Test
        @DisplayName("Khách đã có voucher → bỏ qua, không phát trùng, không ném lỗi")
        void boQuaKhachDaCo() {
            when(userVoucherRepository.existsByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(true);
            when(userVoucherRepository.existsByUserIdAndVoucherId("u2", "voucher-1")).thenReturn(false);
            when(userRepository.findById("u2")).thenReturn(Optional.of(other("u2")));

            int issued = voucherWalletService.assignToUsers("voucher-1", List.of("u1", "u2"));

            assertEquals(1, issued);
            verify(userVoucherRepository, org.mockito.Mockito.times(1)).save(any(UserVoucher.class));
        }

        @Test
        @DisplayName("Danh sách rỗng → 0, không chạm DB")
        void danhSachRong() {
            assertEquals(0, voucherWalletService.assignToUsers("voucher-1", List.of()));
            verify(userVoucherRepository, never()).save(any());
        }

        @Test
        @DisplayName("Voucher không dùng được → VOUCHER_NOT_USABLE")
        void voucherKhongDungDuoc() {
            when(voucherService.isVoucherUsable(any(Voucher.class))).thenReturn(false);

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.assignToUsers("voucher-1", List.of("u1")));
            assertEquals(ErrorCode.VOUCHER_NOT_USABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Id khách lạ → bỏ qua người đó, vẫn phát cho người còn lại")
        void boQuaIdLa() {
            when(userVoucherRepository.existsByUserIdAndVoucherId(any(), any())).thenReturn(false);
            when(userRepository.findById("u1")).thenReturn(Optional.of(user));
            when(userRepository.findById("missing")).thenReturn(Optional.empty());

            int issued = voucherWalletService.assignToUsers("voucher-1", List.of("u1", "missing"));

            assertEquals(1, issued);
        }

        @Test
        @DisplayName("Voucher không tồn tại → VOUCHER_NOT_USABLE")
        void voucherKhongTonTai() {
            when(voucherRepository.findById("missing")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> voucherWalletService.assignToUsers("missing", List.of("u1")));
            assertEquals(ErrorCode.VOUCHER_NOT_USABLE, ex.getErrorCode());
        }
    }

    // ==================================================================
    // Danh sách khách đã nhận (trang chi tiết voucher)
    // ==================================================================

    @Nested
    @DisplayName("getHolders — khách đã nhận")
    class Holders {

        @Test
        @DisplayName("Trả kèm tên + email khách, không cần gọi thêm API")
        void traKemThongTinKhach() {
            user.setFullName("Nguyễn Văn A");
            user.setEmail("vana@example.com");
            when(userVoucherRepository.findHoldersByVoucherId("voucher-1"))
                    .thenReturn(List.of(voucher(UserVoucherStatus.AVAILABLE, null)));

            var res = voucherWalletService.getHolders("voucher-1");

            assertEquals(1, res.size());
            assertEquals("Nguyễn Văn A", res.get(0).getUserName());
            assertEquals("vana@example.com", res.get(0).getUserEmail());
            assertEquals(UserVoucherSource.CLAIMED, res.get(0).getSource());
        }

        @Test
        @DisplayName("Chưa ai nhận → danh sách rỗng")
        void chuaAiNhan() {
            when(userVoucherRepository.findHoldersByVoucherId("voucher-1")).thenReturn(List.of());

            assertTrue(voucherWalletService.getHolders("voucher-1").isEmpty());
        }
    }
}
