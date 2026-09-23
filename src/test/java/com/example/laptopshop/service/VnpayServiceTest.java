package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.config.VnpayConfig;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.Payment;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.dto.response.Client.VnpayCreateResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;

/**
 * Test thuần (không Spring context) cho VnpayService — xác minh chữ ký, dựng URL
 * thanh toán và xử lý callback.
 *
 * <p>Kiến trúc: mỗi lần bấm "Thanh toán" sinh một {@link Payment} với
 * {@code txnRef} riêng ({@code <orderCode>A<n>}), nên callback tra theo
 * {@code txnRef} chứ KHÔNG phải {@code orderCode}. Số tiền lấy từ
 * {@code Payment.amount} (đã chụp lúc mở cổng), không đọc lại từ đơn.
 *
 * <p>Hai thuật toán chữ ký được hỗ trợ: {@code hmacsha512} (mặc định, theo code
 * mẫu chính thức của VNPay) và {@code sha256} (băm chuỗi nối). Cả hai đều có
 * test vì đổi config là đổi thuật toán, không sửa code.
 */
@ExtendWith(MockitoExtension.class)
class VnpayServiceTest {

    private static final String ORDER_CODE = "LS12345678";
    private static final String TXN_REF = "LS12345678A1";
    private static final long AMOUNT = 1_289_000L;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    private VnpayConfig config;
    private VnpayService vnpayService;

    @BeforeEach
    void setUp() {
        config = new VnpayConfig();
        config.setTmnCode("LAPTOPSHOP");
        config.setHashSecret("SECRET-HASH-KEY");
        config.setHashType("sha256");
        config.setUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        config.setVersion("2.1.0");
        config.setCurrency("VND");
        config.setLocale("vn");
        config.setOrderType("other");
        config.setReturnUrl("http://localhost:8080/api/v1/client/payments/vnpay/return");
        config.setIpnUrl("http://localhost:8080/api/v1/client/payments/vnpay/ipn");
        config.setFeReturnUrl("http://localhost:4200/payment-result");
        vnpayService = new VnpayService(config, orderService, paymentService);
    }

    // ===== Chữ ký =====

    /** Chữ ký đúng công thức SHA-256(secret + hashData) thì được chấp nhận. */
    @Test
    void verifySignature_acceptsValidSha256Hash() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "00");

        assertTrue(vnpayService.verifySignature(params));
    }

    /** Sửa 1 ký tự dữ liệu → chữ ký không còn khớp (chống gian lận số tiền). */
    @Test
    void verifySignature_rejectsTamperedData() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", String.valueOf(AMOUNT * 100));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_SecureHashType", "sha256");
        // Chữ ký tính trên số tiền khác nhưng dữ liệu gửi đi là số tiền thật.
        params.put("vnp_SecureHash",
                sha256Hex(config.getHashSecret() + hashData(AMOUNT * 100 + 1, "00")));

        assertFalse(vnpayService.verifySignature(params));
    }

    /** Thiếu vnp_SecureHash → từ chối, không ném lỗi. */
    @Test
    void verifySignature_rejectsMissingHash() {
        assertFalse(vnpayService.verifySignature(Map.of("vnp_TxnRef", TXN_REF)));
    }

    /** hash-type = hmacsha512 → ký HMAC-SHA512, đúng thuật toán mặc định. */
    @Test
    void verifySignature_acceptsValidHmacSha512() throws Exception {
        config.setHashType("hmacsha512");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", String.valueOf(AMOUNT * 100));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", TXN_REF);
        // vnp_SecureHashType vắng mặt → verifySignature dùng hash-type ở config.
        params.put("vnp_SecureHash",
                hmacSha512Hex(config.getHashSecret(), hashData(AMOUNT * 100, "00")));

        assertTrue(vnpayService.verifySignature(params));
    }

    /** Ký bằng HMAC nhưng portal cấu hình sha256 → không khớp, phải từ chối. */
    @Test
    void verifySignature_rejectsHashFromWrongAlgorithm() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", String.valueOf(AMOUNT * 100));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_SecureHash",
                hmacSha512Hex(config.getHashSecret(), hashData(AMOUNT * 100, "00")));

        assertFalse(vnpayService.verifySignature(params));
    }

    // ===== Mở cổng thanh toán =====

    /** /create dựng URL đúng sandbox: tiền x100, txnRef của LẦN THỬ, có IpAddr. */
    @Test
    void createPayment_buildsCorrectUrl() {
        Order order = vnpayOrder();
        Payment payment = pendingPayment(order);
        when(orderService.getOrderForPayment(ORDER_CODE, "user-1")).thenReturn(order);
        when(paymentService.startPayment(order)).thenReturn(payment);

        VnpayCreateResponse res = vnpayService.createPayment(ORDER_CODE, "user-1", "203.0.113.7");

        assertNotNull(res.getPaymentUrl());
        assertTrue(res.getPaymentUrl().startsWith(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(res.getPaymentUrl().contains("vnp_TmnCode=LAPTOPSHOP"));
        // 1.289.000₫ x 100 = 128.900.000 (đơn vị tiền VNPay).
        assertTrue(res.getPaymentUrl().contains("vnp_Amount=128900000"));
        // vnp_TxnRef là mã LẦN THỬ (có hậu tố A1), không phải mã đơn.
        assertTrue(res.getPaymentUrl().contains("vnp_TxnRef=" + TXN_REF));
        assertTrue(res.getPaymentUrl().contains("vnp_IpAddr=203.0.113.7"));
        // VNPay bỏ tham số này khi verify → gửi vào sẽ làm lệch chữ ký.
        assertFalse(res.getPaymentUrl().contains("vnp_SecureHashType"));
        assertTrue(res.getPaymentUrl().contains("vnp_SecureHash="));
        // FE cần mã ĐƠN để hiển thị, không phải mã giao dịch.
        assertEquals(ORDER_CODE, res.getOrderCode());
    }

    /** Chữ ký trong URL phải khớp đúng query string đã gửi (chống lệch ký/gửi). */
    @Test
    void createPayment_signsTheExactQueryStringSent() throws Exception {
        Order order = vnpayOrder();
        Payment payment = pendingPayment(order);
        when(orderService.getOrderForPayment(ORDER_CODE, "user-1")).thenReturn(order);
        when(paymentService.startPayment(order)).thenReturn(payment);

        String url = vnpayService.createPayment(ORDER_CODE, "user-1", "127.0.0.1").getPaymentUrl();
        String query = url.substring(url.indexOf('?') + 1);
        String sentHash = query.substring(query.indexOf("vnp_SecureHash=") + "vnp_SecureHash=".length());
        String sentData = query.substring(0, query.indexOf("&vnp_SecureHash="));

        assertEquals(sha256Hex(config.getHashSecret() + sentData), sentHash);
        // vnp_OrderInfo có dấu cách → phải URL-encode, không được cắt query string.
        assertTrue(sentData.contains("vnp_OrderInfo="));
        assertFalse(sentData.contains("Thanh toan don " + ORDER_CODE + "&"));
    }

    /** Chưa cấu hình key → báo lỗi rõ thay vì dựng URL sai. */
    @Test
    void createPayment_throwsWhenNotConfigured() {
        config.setTmnCode("");
        config.setHashSecret("");

        AppException ex = assertThrows(AppException.class,
                () -> vnpayService.createPayment(ORDER_CODE, "user-1", "127.0.0.1"));

        assertEquals(ErrorCode.VNPAY_NOT_CONFIGURED, ex.getErrorCode());
    }

    // ===== Callback /return (trình duyệt) =====

    /** /return thành công → redirect FE kèm mã ĐƠN + trạng thái success. */
    @Test
    void handleReturn_redirectsToFeWithStatus() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "00");
        Payment payment = pendingPayment(vnpayOrder());
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(payment);
        when(paymentService.completePayment(payment, "00", params)).thenReturn(true);

        String url = vnpayService.handleReturn(params);

        assertTrue(url.startsWith("http://localhost:4200/payment-result?"));
        assertTrue(url.contains("orderCode=" + ORDER_CODE));
        assertTrue(url.contains("status=success"));
        verify(paymentService).completePayment(payment, "00", params);
    }

    /** Cổng báo lỗi (RspCode != 00) → redirect status=failed nhưng VẪN ghi nhận. */
    @Test
    void handleReturn_marksFailedWhenGatewayRejects() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "24");
        Payment payment = pendingPayment(vnpayOrder());
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(payment);
        when(paymentService.completePayment(payment, "24", params)).thenReturn(true);

        String url = vnpayService.handleReturn(params);

        assertTrue(url.contains("status=failed"));
        assertTrue(url.contains("code=24"));
        verify(paymentService).completePayment(payment, "24", params);
    }

    /** Chữ ký sai → redirect failed, KHÔNG tra DB, KHÔNG cập nhật đơn. */
    @Test
    void handleReturn_forwardsBadSignatureAsFailure() {
        Map<String, String> params = Map.of(
                "vnp_TxnRef", TXN_REF,
                "vnp_ResponseCode", "00",
                "vnp_SecureHash", "sai");

        String url = vnpayService.handleReturn(params);

        assertTrue(url.startsWith("http://localhost:4200/payment-result?"));
        assertTrue(url.contains("status=failed"));
        assertTrue(url.contains("code=invalid_signature"));
        verifyNoInteractions(paymentService);
    }

    // ===== IPN (server VNPay gọi) =====

    /** IPN hợp lệ → RspCode 00. */
    @Test
    void handleIpn_returnsConfirmSuccess() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "00");
        Payment payment = pendingPayment(vnpayOrder());
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(payment);
        when(paymentService.completePayment(payment, "00", params)).thenReturn(true);

        Map<String, String> ipn = vnpayService.handleIpn(params);

        assertEquals("00", ipn.get("RspCode"));
    }

    /** Chữ ký sai → 97, không đụng tới đơn. */
    @Test
    void handleIpn_rejectsBadSignature() {
        Map<String, String> ipn = vnpayService.handleIpn(
                Map.of("vnp_TxnRef", TXN_REF, "vnp_SecureHash", "sai"));

        assertEquals("97", ipn.get("RspCode"));
        verifyNoInteractions(paymentService);
    }

    /** Không có lần thử nào khớp txnRef → 01 (Order not found). */
    @Test
    void handleIpn_returns01WhenTxnRefUnknown() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "00");
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(null);

        assertEquals("01", vnpayService.handleIpn(params).get("RspCode"));
    }

    /** Số tiền callback lệch số tiền đã chụp → 04, không cập nhật đơn. */
    @Test
    void handleIpn_returns04OnAmountMismatch() throws Exception {
        // Callback khai ít hơn 1.000₫ so với số tiền của lần thử.
        Map<String, String> params = callbackParams(AMOUNT - 1_000, "00");
        Payment payment = pendingPayment(vnpayOrder());
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(payment);

        assertEquals("04", vnpayService.handleIpn(params).get("RspCode"));
        verify(paymentService, never()).completePayment(any(), any(), any());
    }

    /** Lần thử đã có kết quả cuối → 02 (đã xác nhận trước đó), idempotent. */
    @Test
    void handleIpn_returns02WhenAlreadyConfirmed() throws Exception {
        Map<String, String> params = callbackParams(AMOUNT, "00");
        Payment payment = pendingPayment(vnpayOrder());
        when(paymentService.findByTxnRef(TXN_REF)).thenReturn(payment);
        when(paymentService.completePayment(payment, "00", params)).thenReturn(false);

        assertEquals("02", vnpayService.handleIpn(params).get("RspCode"));
    }

    // ===== Helper =====

    private Order vnpayOrder() {
        Order order = new Order();
        order.setOrderCode(ORDER_CODE);
        order.setTotalPrice(AMOUNT);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        return order;
    }

    /** Lần thử đang chờ khách trả tiền — số tiền đã chụp lúc mở cổng. */
    private Payment pendingPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTxnRef(TXN_REF);
        payment.setAttemptNo(1);
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(AMOUNT);
        return payment;
    }

    /**
     * Tham số callback đã ký hợp lệ. Dùng {@code vnp_SecureHashType=sha256} để
     * test độc lập với hash-type ở config.
     */
    private Map<String, String> callbackParams(long amount, String responseCode) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", String.valueOf(amount * 100));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_SecureHashType", "sha256");
        params.put("vnp_SecureHash",
                sha256Hex(config.getHashSecret() + hashData(amount * 100, responseCode)));
        return params;
    }

    /** Chuỗi để ký — cùng cách sắp xếp và bỏ khóa mà VnpayService dùng. */
    private static String hashData(long vnpAmount, String responseCode) {
        return "vnp_Amount=" + vnpAmount
                + "&vnp_ResponseCode=" + responseCode
                + "&vnp_TxnRef=" + TXN_REF;
    }

    private static String sha256Hex(String input) throws Exception {
        byte[] raw = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        return toHex(raw);
    }

    private static String hmacSha512Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
