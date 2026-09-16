package com.example.laptopshop.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Đọc thông tin thiết bị từ HTTP request.
 *
 * <p>{@code deviceId} do FE sinh (UUID, localStorage) và gửi qua header
 * {@code X-Device-Id}; {@code User-Agent} do trình duyệt tự gửi; IP lấy từ
 * remote address (đã tính X-Forwarded-For nếu chạy sau proxy).
 */
public final class DeviceRequestUtils {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private DeviceRequestUtils() {
        // utility class
    }

    /** Null nếu FE chưa gửi header (client cũ) -> bỏ qua logic giới hạn thiết bị. */
    public static String getDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        // Chặn chuỗi quá dài / ký tự lạ để không làm bẩn key Redis.
        String trimmed = deviceId.trim();
        if (trimmed.length() > 64 || !trimmed.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return trimmed;
    }

    public static String getUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Phần tử đầu tiên là client gốc.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
