package com.example.laptopshop.utils;

/**
 * Parse header {@code User-Agent} thành tên thiết bị thân thiện để hiển thị
 * (vd "Chrome - Windows", "Safari - iOS").
 *
 * <p>Regex thuần, KHÔNG dùng thư viện ngoài (không thêm dependency chỉ để hiển
 * thị tên). Whitelist trình duyệt/hệ điều hành phổ biến; nhận diện không được
 * thì trả "Trình duyệt khác" / "Hệ điều hành khác" thay vì chuỗi UA thô.
 *
 * <p>Đây chỉ là metadata HIỂN THỊ — KHÔNG dùng để định danh thiết bị (định danh
 * dùng deviceId do FE sinh, xem {@link com.example.laptopshop.domain.UserDeviceSession}).
 * Vì vậy parse sai cũng không ảnh hưởng logic bảo mật.
 */
public final class DeviceNameParser {

    private DeviceNameParser() {
        // utility class
    }

    private static final String UNKNOWN_BROWSER = "Trình duyệt khác";
    private static final String UNKNOWN_OS = "Hệ điều hành khác";

    // Thứ tự QUAN TRỌNG: phải check các token đặc trưng trước các token chung.
    // - Edge/Opera/Oculus đều chứa "Chrome" -> check trước Chrome.
    // - Chrome trên iOS chứa cả "CriOS" lẫn "Safari" -> check CriOS trước Safari.
    private static final String[][] BROWSERS = {
            { "Edg/", "Edge" },
            { "EdgA/", "Edge" },
            { "OPR/", "Opera" },
            { "Opera", "Opera" },
            { "SamsungBrowser", "Samsung Internet" },
            { "CriOS", "Chrome" },
            { "FxiOS", "Firefox" },
            { "Firefox", "Firefox" },
            { "Chrome", "Chrome" },
            { "CocCoc", "CocCoc" },
            { "Safari", "Safari" },
    };

    // Thứ tự QUAN TRỌNG: Android chứa "Linux" -> check Android trước Linux.
    // iPad/iPhone dùng chung "like Mac OS X" -> check iPad trước iPhone.
    private static final String[][] OPERATING_SYSTEMS = {
            { "Windows", "Windows" },
            { "Android", "Android" },
            { "iPad", "iPadOS" },
            { "iPhone", "iOS" },
            { "Mac OS X", "macOS" },
            { "Macintosh", "macOS" },
            { "Linux", "Linux" },
    };

    /**
     * @param userAgent chuỗi User-Agent thô (có thể null/rỗng)
     * @return tên hiển thị dạng "Chrome - Windows", hoặc "Trình duyệt khác - Hệ điều hành khác"
     */
    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_BROWSER + " - " + UNKNOWN_OS;
        }
        return detect(userAgent, BROWSERS, UNKNOWN_BROWSER)
                + " - "
                + detect(userAgent, OPERATING_SYSTEMS, UNKNOWN_OS);
    }

    /** Duyệt bảng theo thứ tự, trả tên đầu tiên mà UA chứa token tương ứng. */
    private static String detect(String userAgent, String[][] table, String fallback) {
        for (String[] entry : table) {
            if (userAgent.contains(entry[0])) {
                return entry[1];
            }
        }
        return fallback;
    }
}
