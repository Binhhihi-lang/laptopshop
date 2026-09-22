package com.example.laptopshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình cổng thanh toán VNPay. Các thông số cứng (version, command, đơn vị
 * tiền...)
 */
@Configuration
@Getter
@Setter
public class VnpayConfig {

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    // Thuật toán chữ ký: hmacsha512 = HMAC-SHA512(hashSecret, hashData) — mặc
    // định vì mọi code mẫu chính thức của VNPay đều ký SHA-512; sha256 =
    // SHA-256(hashSecret + hashData). VNPay verify theo cấu hình trên portal
    // (không có tham số vnp_SecureHashType trong tài liệu) nên giá trị này
    // BẮT BUỘC khớp thuật toán đã chọn ở portal.
    @Value("${vnpay.hash-type}")
    private String hashType;

    // Sandbox mặc định; đổi sang endpoint production khi go live.
    @Value("${vnpay.url}")
    private String url;

    @Value("${vnpay.version}")
    private String version;

    @Value("${vnpay.currency}")
    private String currency;

    @Value("${vnpay.locale}")
    private String locale;

    @Value("${vnpay.order-type}")
    private String orderType;

    // URL VNPay gọi tới: return (trình duyệt redirect) + ipn (server gọi).
    @Value("${vnpay.return-url}")
    private String returnUrl;

    @Value("${vnpay.ipn-url}")
    private String ipnUrl;

    // Nơi FE hiển thị kết quả sau khi VNPay redirect trình duyệt về /return.
    @Value("${vnpay.fe-return-url}")
    private String feReturnUrl;

    public boolean isConfigured() {
        return this.tmnCode != null && !this.tmnCode.isBlank()
                && this.hashSecret != null && !this.hashSecret.isBlank();
    }
}