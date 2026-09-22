package com.example.laptopshop.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.example.laptopshop.config.VnpayConfig;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.Payment;
import com.example.laptopshop.dto.response.Client.VnpayCreateResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Cổng thanh toán VNPay (luồng "pay" qua query string, chuẩn v2.1.0).
 *
 * Chữ ký vnp_SecureHash tính trên chuỗi "k1=v1&k2=v2" của MỌI tham số vnp_*
 * (trừ vnp_SecureHash và vnp_SecureHashType), sắp xếp theo key tăng dần, key
 * và value đều URL-encode ASCII. Thuật toán theo cấu hình vnpay.hash-type:
 * - hmacsha512 → HMAC-SHA512(hashSecret, hashData) — mặc định, đúng như mọi
 *   code mẫu chính thức của VNPay
 * - sha256     → SHA-256(hashSecret + hashData)
 * Tài liệu VNPay KHÔNG định nghĩa tham số vnp_SecureHashType, nên cổng verify
 * bằng thuật toán đã cấu hình ở portal — giá trị cấu hình phải khớp portal.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnpayService {

    // Tên thuật toán theo đúng quy ước của từng API Java: Mac dùng "HmacSHA512"
    // (không gạch), MessageDigest dùng "SHA-256" (có gạch) — không dùng lẫn được.
    static final String HMAC_SHA512 = "HmacSHA512";
    static final String SHA_256 = "SHA-256";
    static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    VnpayConfig config;
    OrderService orderService;
    PaymentService paymentService;

    /** Kết quả xử lý callback VNPay — bản đồ sang RspCode trả về cho VNPay. */
    public enum VnpayCallbackResult {
        UPDATED, // đã cập nhật xong (PAID hoặc FAILED) → trả "00"
        ALREADY_CONFIRMED, // lần thử này đã có kết quả trước đó → trả "02"
        ORDER_NOT_FOUND, // không có lần thử nào khớp vnp_TxnRef → trả "01"
        AMOUNT_MISMATCH // số tiền không khớp → trả "04"
    }

    /** Chặn mọi request tới VNPay khi merchant chưa được cấu hình key. */
    private void checkConfigured() {
        if (!this.config.isConfigured()) {
            throw new AppException(ErrorCode.VNPAY_NOT_CONFIGURED);
        }
    }

    /**
     * Mở một lần thử thanh toán cho đơn rồi dựng URL đưa trình duyệt sang cổng
     * VNPay. Mỗi lần gọi (kể cả thanh toán lại) tạo một {@code txnRef} mới vì
     * VNPay không cho trùng mã giao dịch.
     */
    public VnpayCreateResponse createPayment(String orderCode, String userId, String ipAddr) {
        checkConfigured();
        Order order = this.orderService.getOrderForPayment(orderCode, userId);
        Payment payment = this.paymentService.startPayment(order);
        return toResponse(order, buildPaymentUrl(payment, ipAddr));
    }

    /**
     * Xử lý callback /return (VNPay redirect trình duyệt về): verify chữ ký →
     * cập nhật đơn → trả URL redirect tiếp trang kết quả bên FE. Chữ ký sai
     * cũng redirect về FE với trạng thái failed (không để trình duyệt thấy
     * JSON lỗi ngay sau khi rời cổng VNPay).
     */
    public String handleReturn(Map<String, String> params) {
        if (!verifySignature(params)) {
            // Chữ ký sai thì KHÔNG tra DB (dữ liệu không đáng tin) — vẫn redirect
            // về FE để trình duyệt không thấy JSON lỗi ngay sau khi rời cổng.
            return failureRedirect("invalid_signature");
        }
        applyCallback(params);
        // FE cần MÃ ĐƠN để hiển thị và cho thanh toán lại, không phải mã giao
        // dịch của lần thử (đã có hậu tố A<n>).
        Payment payment = this.paymentService.findByTxnRef(params.get("vnp_TxnRef"));
        String orderCode = payment == null ? null : payment.getOrder().getOrderCode();
        String responseCode = params.get("vnp_ResponseCode");
        String status = "00".equals(responseCode) ? "success" : "failed";
        return this.config.getFeReturnUrl()
                + "?orderCode=" + encode(orderCode)
                + "&status=" + status
                + "&code=" + encode(responseCode);
    }

    // URL redirect về FE khi không xác định được đơn (chữ ký sai / mã lạ).
    private String failureRedirect(String code) {
        return this.config.getFeReturnUrl()
                + "?status=failed"
                + "&code=" + code;
    }

    /**
     * Xử lý IPN (server VNPay gọi, nguồn chính thức): cập nhật đơn rồi trả
     * JSON theo chuẩn VNPay để xác nhận merchant đã nhận được giao dịch.
     */
    public Map<String, String> handleIpn(Map<String, String> params) {
        if (!verifySignature(params)) {
            return ipnResponse("97", "Invalid Signature");
        }
        return switch (applyCallback(params)) {
            case ORDER_NOT_FOUND -> ipnResponse("01", "Order not found");
            case AMOUNT_MISMATCH -> ipnResponse("04", "Invalid amount");
            case ALREADY_CONFIRMED -> ipnResponse("02", "Order already confirmed");
            default -> ipnResponse("00", "Confirm Success");
        };
    }

    /**
     * Xác minh chữ ký vnp_SecureHash của callback VNPay. Thuật toán ưu tiên
     * theo vnp_SecureHashType do VNPay gửi kèm; thiếu thì dùng cấu hình.
     */
    public boolean verifySignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        checkConfigured();
        String hashType = params.getOrDefault("vnp_SecureHashType", this.config.getHashType());
        return sign(hashData(params), hashType).equalsIgnoreCase(received);
    }

    // ===== Nội bộ =====

    /**
     * Callback tra thẳng theo vnp_TxnRef để biết đang cập nhật lần thử nào —
     * nhờ đó không phải parse chuỗi và không lẫn giữa các lần thanh toán lại.
     */
    private VnpayCallbackResult applyCallback(Map<String, String> params) {
        Payment payment = this.paymentService.findByTxnRef(params.get("vnp_TxnRef"));
        if (payment == null) {
            return VnpayCallbackResult.ORDER_NOT_FOUND;
        }
        long amount;
        try {
            amount = Long.parseLong(params.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException e) {
            amount = -1L; // lệch tiền → trả AMOUNT_MISMATCH
        }
        if (payment.getAmount() * 100L != amount) {
            return VnpayCallbackResult.AMOUNT_MISMATCH;
        }
        boolean updated = this.paymentService.completePayment(
                payment, params.get("vnp_ResponseCode"), params);
        return updated ? VnpayCallbackResult.UPDATED : VnpayCallbackResult.ALREADY_CONFIRMED;
    }

    private String buildPaymentUrl(Payment payment, String ipAddr) {
        Order order = payment.getOrder();
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", this.config.getVersion());
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", this.config.getTmnCode());
        // Đơn vị VND, nhân 100 theo chuẩn VNPay.
        vnpParams.put("vnp_Amount", String.valueOf(payment.getAmount() * 100L));
        vnpParams.put("vnp_CurrCode", this.config.getCurrency());
        // Mã giao dịch của LẦN THỬ này (không phải mã đơn): VNPay yêu cầu mã
        // duy nhất cho mỗi giao dịch nên thanh toán lại phải có mã mới.
        vnpParams.put("vnp_TxnRef", payment.getTxnRef());
        // vnp_OrderInfo BẮT BUỘC tiếng Việt không dấu: chữ ký mã hóa ASCII.
        vnpParams.put("vnp_OrderInfo", "LaptopShop - Thanh toan don " + order.getOrderCode());
        vnpParams.put("vnp_OrderType", this.config.getOrderType());
        vnpParams.put("vnp_Locale", this.config.getLocale());
        vnpParams.put("vnp_IpAddr", ipAddr);
        vnpParams.put("vnp_CreateDate", now.format(TS_FORMAT));
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(TS_FORMAT));
        vnpParams.put("vnp_ReturnUrl", this.config.getReturnUrl());
        // KHÔNG gửi vnp_IpnUrl (khai báo ở portal VNPay) và vnp_SecureHashType
        // (VNPay tự loại bỏ khi verify) — gửi vào sẽ làm lệch chữ ký.

        // Query string CHÍNH LÀ hashData đã URL-encode: vừa ký vừa gửi cùng
        // một chuỗi nên không thể lệch nhau.
        String hashData = hashData(vnpParams);
        String hash = sign(hashData, this.config.getHashType());
        return this.config.getUrl() + "?" + hashData + "&vnp_SecureHash=" + hash;
    }

    private VnpayCreateResponse toResponse(Order order, String paymentUrl) {
        VnpayCreateResponse res = new VnpayCreateResponse();
        res.setPaymentUrl(paymentUrl);
        res.setOrderCode(order.getOrderCode());
        return res;
    }

    /**
     * Chuỗi dữ liệu để ký: sắp xếp key tăng dần, bỏ vnp_SecureHash/
     * vnp_SecureHashType và giá trị rỗng, value URL-encode ASCII — đúng theo
     * code demo chính thức của VNPay (hashAllFields). Chuỗi này cũng là query
     * string gửi sang VNPay nên bên nhận tính lại ra đúng chữ ký.
     */
    private String hashData(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }
            String value = params.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(encodeAscii(value));
        }
        return sb.toString();
    }

    /** Encode giá trị để ký/gửi VNPay — ASCII theo đúng demo VNPay. */
    private static String encodeAscii(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    /** Encode tham số cho URL redirect về FE (tiếng Việt → UTF-8). */
    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Ký hashData theo thuật toán đã cấu hình — CHỈ CHẠY MỘT nhánh mỗi lần, tùy
     * {@code vnpay.hash-type}; nhánh còn lại là để đổi qua config mà không phải
     * sửa code. Hai công thức khác nhau về bản chất (HMAC có khóa vs băm chuỗi
     * nối) nên kết quả không thể trùng, phải khớp thuật toán ở portal VNPay.
     */
    private String sign(String hashData, String hashType) {
        String secret = this.config.getHashSecret();
        try {
            if ("hmacsha512".equalsIgnoreCase(hashType)) {
                Mac mac = Mac.getInstance(HMAC_SHA512);
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
                return toHex(mac.doFinal(hashData.getBytes(StandardCharsets.UTF_8)));
            }
            // SHA-256 không có khái niệm khóa nên phải tự nối secret vào trước.
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return toHex(digest.digest((secret + hashData).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khi mã hóa chữ ký VNPay", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static Map<String, String> ipnResponse(String rspCode, String message) {
        return Map.of("RspCode", rspCode, "Message", message);
    }
}