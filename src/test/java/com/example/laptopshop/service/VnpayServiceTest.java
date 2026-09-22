package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.config.VnpayConfig;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.dto.response.Client.VnpayCreateResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;

/**
 * Test thuần (không Spring context) cho VnpayService — xác minh chữ ký và
 * dựng URL thanh toán theo chuẩn VNPay: SHA-256(hashSecret + hashData).
 */
@ExtendWith(MockitoExtension.class)
class VnpayServiceTest {

    @Mock
    private OrderService orderService;

    private VnpayConfig config;
    private VnpayService vnpayService;

    @BeforeEach
    void setUp() {
        config = new VnpayConfig();
        config.setTmnCode("LAPTOPSHOP");
        config.setHashSecret("SECRET-HASH-KEY");
        config.setHashType("sha256");
        config.setUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        config.setReturnUrl("http://localhost:8080/api/v1/client/payments/vnpay/return");
        config.setIpnUrl("http://localhost:8080/api/v1/client/payments/vnpay/ipn");
        config.setFeReturnUrl("http://localhost:4200/payment-result");
        vnpayService = new VnpayService(config, orderService);
    }

    /** Chữ ký đúng công thức SHA-256(secret + hashData) thì được chấp nhận. */
    @Test
    void verifySignature_acceptsValidSha256Hash() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "128900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "LS12345678");
        params.put("vnp_SecureHashType", "sha256");

        String hashData = "vnp_Amount=128900000&vnp_ResponseCode=00&vnp_TxnRef=LS12345678";
        params.put("vnp_SecureHash", sha256Hex(config.getHashSecret() + hashData));

        assertTrue(vnpayService.verifySignature(params));
    }

    /** Sửa 1 ký tự dữ liệu → chữ ký không còn khớp (chống gian lận số tiền). */
    @Test
    void verifySignature_rejectsTamperedData() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "128900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "LS12345678");
        params.put("vnp_SecureHashType", "sha256");

        // Chữ ký tính trên số tiền khác (128900001) nhưng dữ liệu gửi là 128900000
        String hashData = "vnp_Amount=128900001&vnp_ResponseCode=00&vnp_TxnRef=LS12345678";
        params.put("vnp_SecureHash", sha256Hex(config.getHashSecret() + hashData));

        assertFalse(vnpayService.verifySignature(params));
    }

    /** Thiếu vnp_SecureHash → từ chối, không ném lỗi. */
    @Test
    void verifySignature_rejectsMissingHash() {
        assertFalse(vnpayService.verifySignature(Map.of("vnp_TxnRef", "LS12345678")));
    }

    /** /create dựng URL đúng sandbox: tiền x100, mã đơn, loại chữ ký. */
    @Test
    void createPayment_buildsCorrectUrl() {
        Order order = new Order();
        order.setOrderCode("LS12345678");
        order.setTotalPrice(1289000L);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        when(orderService.getOrderForPayment("LS12345678", "user-1")).thenReturn(order);

        VnpayCreateResponse res = vnpayService.createPayment("LS12345678", "user-1", "203.0.113.7");

        assertNotNull(res.getPaymentUrl());
        assertTrue(res.getPaymentUrl().startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(res.getPaymentUrl().contains("vnp_TmnCode=LAPTOPSHOP"));
        // 1.289.000₫ x 100 = 128.900.000 (đơn vị VNPay)
        assertTrue(res.getPaymentUrl().contains("vnp_Amount=128900000"));
        assertTrue(res.getPaymentUrl().contains("vnp_TxnRef=LS12345678"));
        // vnp_IpAddr bắt buộc; vnp_SecureHashType KHÔNG được gửi (VNPay bỏ khi verify).
        assertTrue(res.getPaymentUrl().contains("vnp_IpAddr=203.0.113.7"));
        assertFalse(res.getPaymentUrl().contains("vnp_SecureHashType"));
        assertTrue(res.getPaymentUrl().contains("vnp_SecureHash="));
        assertEquals("LS12345678", res.getOrderCode());
    }

    /** Chữ ký trong URL phải khớp hashData đã gửi (tự kiểm tra chống lệch). */
    @Test
    void createPayment_signsTheExactQueryStringSent() throws Exception {
        Order order = new Order();
        order.setOrderCode("LS99999999");
        order.setTotalPrice(500000L);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        when(orderService.getOrderForPayment("LS99999999", "user-1")).thenReturn(order);

        String url = vnpayService.createPayment("LS99999999", "user-1", "127.0.0.1").getPaymentUrl();
        String query = url.substring(url.indexOf('?') + 1);
        String sentHash = query.substring(query.indexOf("vnp_SecureHash=") + "vnp_SecureHash=".length());
        String sentData = query.substring(0, query.indexOf("&vnp_SecureHash="));

        assertEquals(sha256Hex(config.getHashSecret() + sentData), sentHash);
        // vnp_OrderInfo có dấu cách → phải được encode thành %20 hoặc +
        assertTrue(sentData.contains("vnp_OrderInfo="));
        assertFalse(sentData.contains("Thanh toan don LS99999999&"));
    }

    /** Chưa cấu hình key → báo lỗi rõ thay vì dựng URL sai. */
    @Test
    void createPayment_throwsWhenNotConfigured() {
        config.setTmnCode("");
        config.setHashSecret("");
        AppException ex = assertThrows(AppException.class,
                () -> vnpayService.createPayment("LS12345678", "user-1", "127.0.0.1"));
        assertEquals(ErrorCode.VNPAY_NOT_CONFIGURED, ex.getErrorCode());
    }

    /** /return thành công → redirect FE kèm trạng thái success + cập nhật đơn. */
    @Test
    void handleReturn_redirectsToFeWithStatus() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "100000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "LS12345678");
        params.put("vnp_SecureHashType", "sha256");
        String hashData = "vnp_Amount=100000&vnp_ResponseCode=00&vnp_TxnRef=LS12345678";
        params.put("vnp_SecureHash", sha256Hex(config.getHashSecret() + hashData));

        String url = vnpayService.handleReturn(params);

        assertTrue(url.startsWith("http://localhost:4200/payment-result?"));
        assertTrue(url.contains("orderCode=LS12345678"));
        assertTrue(url.contains("status=success"));
        verify(orderService).completeVnpayPayment(eq("LS12345678"), isNull(), eq(100000L), eq("00"));
    }

    /** Chữ ký sai → redirect FE kèm failed, KHÔNG ném lỗi / không cập nhật đơn. */
    @Test
    void handleReturn_forwardsBadSignatureAsFailure() {
        Map<String, String> params = Map.of(
                "vnp_TxnRef", "LS12345678",
                "vnp_ResponseCode", "00",
                "vnp_SecureHash", "sai");
        String url = vnpayService.handleReturn(params);
        assertTrue(url.startsWith("http://localhost:4200/payment-result?"));
        assertTrue(url.contains("status=failed"));
        assertTrue(url.contains("code=invalid_signature"));
        verify(orderService, never()).completeVnpayPayment(any(), any(), anyLong(), any());
    }

    /** IPN hợp lệ → RspCode 00; chữ ký sai → 97. */
    @Test
    void handleIpn_returnsConfirmSuccess() throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "100000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "LS12345678");
        params.put("vnp_SecureHashType", "sha256");
        String hashData = "vnp_Amount=100000&vnp_ResponseCode=00&vnp_TxnRef=LS12345678";
        params.put("vnp_SecureHash", sha256Hex(config.getHashSecret() + hashData));
        when(orderService.completeVnpayPayment(eq("LS12345678"), any(), eq(100000L), eq("00")))
                .thenReturn(OrderService.VnpayCallbackResult.UPDATED);

        Map<String, String> ipn = vnpayService.handleIpn(params);

        assertEquals("00", ipn.get("RspCode"));
    }

    @Test
    void handleIpn_rejectsBadSignature() {
        Map<String, String> ipn = vnpayService.handleIpn(
                Map.of("vnp_TxnRef", "LS12345678", "vnp_SecureHash", "sai"));
        assertEquals("97", ipn.get("RspCode"));
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] raw = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}