package com.example.laptopshop.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.RefreshToken;
import com.example.laptopshop.domain.UserDeviceSession;
import com.example.laptopshop.repository.RefreshTokenRepository;
import com.example.laptopshop.repository.UserDeviceSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quản lý phiên đăng nhập theo THIẾT BỊ.
 *
 * <p>Mô hình: 1 user + 1 deviceId = 1 phiên (key {@code userId:deviceId}). Nhờ
 * vậy đếm số phiên theo userId chính là đếm số thiết bị đang đăng nhập; login
 * lại trên cùng thiết bị GHI ĐÈ phiên cũ nên không bao giờ chiếm thêm slot.
 *
 * <p>Thu hồi phiên = xóa {@link UserDeviceSession} + mọi
 * {@link RefreshToken} của thiết bị đó. Access token còn hạn (6h) bị chặn ngay
 * ở request kế tiếp nhờ bước kiểm tra trong
 * {@code CustomJwtDecoder} -> hiệu lực tức thời, không phải chờ hết hạn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final UserDeviceSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // Số thiết bị tối đa cho mỗi user (CUSTOMER). ADMIN/STAFF được miễn -> không
    // dùng giá trị này, xem AuthenticationService.authenticate.
    // KHÔNG khai báo final: @Value inject qua field, final sẽ khiến Lombok đưa
    // nó vào constructor và Spring không set được.
    @Value("${app.device.max-sessions}")
    private int maxSessions;

    // Chỉ ghi lại lastActiveAt nếu lần trước đã quá lâu -> tránh 1 lượt WRITE
    // Redis trên MỖI request.
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    // ================== GHI ==================

    /**
     * Tạo mới hoặc thay thế phiên của thiết bị này (đã login thành công).
     * Trả về phiên sau khi lưu để lấy createdAt phục vụ hiển thị.
     */
    public UserDeviceSession registerOrReplace(String userId, String deviceId,
            String deviceName, String ipAddress, long ttlSeconds) {
        LocalDateTime now = LocalDateTime.now();
        String id = UserDeviceSession.buildId(userId, deviceId);

        // Giữ nguyên createdAt nếu thiết bị này đã có phiên trước đó -> UI hiển
        // thị đúng "đăng nhập lần đầu lúc nào".
        LocalDateTime createdAt = this.sessionRepository.findById(id)
                .map(UserDeviceSession::getCreatedAt)
                .orElse(now);

        UserDeviceSession session = UserDeviceSession.builder()
                .id(id)
                .userId(userId)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .createdAt(createdAt)
                .lastActiveAt(now)
                .ttl(ttlSeconds)
                .build();

        return this.sessionRepository.save(session);
    }

    /**
     * Cập nhật lastActiveAt (có throttle). Gọi mỗi request đã xác thực.
     * Không làm gì nếu phiên không tồn tại hoặc chưa tới hạn ghi.
     */
    public void touch(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String id = UserDeviceSession.buildId(userId, deviceId);
        this.sessionRepository.findById(id).ifPresent(session -> {
            LocalDateTime now = LocalDateTime.now();
            if (session.getLastActiveAt() != null
                    && Duration.between(session.getLastActiveAt(), now).compareTo(TOUCH_INTERVAL) < 0) {
                return; // chưa tới hạn -> bỏ qua, tiết kiệm 1 lượt WRITE
            }
            session.setLastActiveAt(now);
            this.sessionRepository.save(session);
        });
    }

    // ================== ĐỌC ==================

    /** Danh sách thiết bị đang đăng nhập của user. */
    public List<UserDeviceSession> listSessions(String userId) {
        return this.sessionRepository.findByUserId(userId);
    }

    /** Số thiết bị đang đăng nhập ( = số phiên, do 1 thiết bị giữ đúng 1 phiên). */
    public int countSessions(String userId) {
        return this.sessionRepository.findByUserId(userId).size();
    }

    public int getMaxSessions() {
        return this.maxSessions;
    }

    /**
     * Phiên của thiết bị này còn sống không.
     *
     * <p>FAIL-OPEN: Redis lỗi thì trả {@code true} (cho request đi qua) thay vì
     * chặn — thà bỏ sót 1 phiên cần đá còn hơn khóa toàn bộ người dùng khi hạ
     * tầng Redis gặp sự cố.
     */
    public boolean isSessionAlive(String userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return true; // token cũ không có deviceId -> không áp dụng giới hạn
        }
        try {
            return this.sessionRepository.existsById(UserDeviceSession.buildId(userId, deviceId));
        } catch (Exception e) {
            log.error("Khong doc duoc phien tu Redis (fail-open, cho request di qua). userId={}, deviceId={}",
                    userId, deviceId, e);
            return true;
        }
    }

    // ================== THU HỒI ==================

    /**
     * Thu hồi phiên của 1 thiết bị: xóa phiên + mọi refresh token của thiết bị
     * đó. Access token còn hạn bị chặn ở request kế tiếp (CustomJwtDecoder).
     */
    public void revokeSession(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        this.sessionRepository.deleteById(UserDeviceSession.buildId(userId, deviceId));
        clearRefreshTokensOfDevice(userId, deviceId);
        log.info("Da thu hoi phien thiet bi. userId={}, deviceId={}", userId, deviceId);
    }

    /** Thu hồi mọi thiết bị TRỪ thiết bị đang dùng (nút "đăng xuất thiết bị khác"). */
    public void revokeAllExcept(String userId, String keepDeviceId) {
        for (UserDeviceSession session : this.sessionRepository.findByUserId(userId)) {
            if (!session.getDeviceId().equals(keepDeviceId)) {
                revokeSession(userId, session.getDeviceId());
            }
        }
    }

    /** Thu hồi toàn bộ phiên của user (khóa tài khoản / đổi mật khẩu / logout all). */
    public void revokeAllSessions(String userId) {
        for (UserDeviceSession session : this.sessionRepository.findByUserId(userId)) {
            this.sessionRepository.deleteById(session.getId());
        }
        log.info("Da thu hoi toan bo phien thiet bi. userId={}", userId);
    }

    /**
     * Xóa mọi refresh token của 1 thiết bị, GIỮ NGUYÊN phiên.
     *
     * <p>Gọi khi login lại trên cùng thiết bị: cặp token mới sắp được cấp, nên
     * refresh token của lần đăng nhập trước phải mất hiệu lực ngay. Nếu không,
     * 1 thiết bị sẽ tồn tại nhiều refresh token hợp lệ song song — trái với mô
     * hình "1 thiết bị = 1 phiên".
     *
     * <p>Khác {@link #revokeSession}: không xóa {@link UserDeviceSession}, nên
     * giữ được {@code createdAt} (mốc đăng nhập đầu tiên) để hiển thị ở UI.
     */
    public void clearRefreshTokensOfDevice(String userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        List<RefreshToken> tokens = this.refreshTokenRepository
                .findByUserIdAndDeviceId(userId, deviceId);
        if (!tokens.isEmpty()) {
            this.refreshTokenRepository.deleteAll(tokens);
        }
    }
}
