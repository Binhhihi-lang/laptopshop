package com.example.laptopshop.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.Payment;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.PaymentRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Vòng đời giao dịch thanh toán của một đơn.
 *
 * <p>Mỗi lần khách bấm "Thanh toán" (hoặc "Thanh toán lại") sinh ra MỘT bản ghi
 * {@link Payment} với {@code txnRef} riêng — VNPay không cho trùng mã giao dịch
 * nên không thể dùng lại mã đơn. Callback của cổng tra thẳng theo {@code txnRef}
 * để biết đang cập nhật lần thử nào, nhờ đó không cần parse chuỗi.
 *
 * <p>{@code Order.paymentStatus} chỉ là trạng thái TỔNG HỢP, luôn suy ra từ các
 * lần thử: có lần PAID ⇒ đơn PAID; ngược lại lấy trạng thái lần thử mới nhất.
 *
 * <p>COD không tạo bản ghi nào ở đây — đơn COD thu tiền mặt khi giao nên
 * {@code Order.paymentStatus} do luồng trạng thái đơn tự set.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {

    /** Số lần thử tối đa cho một đơn — chặn khách spam nút thanh toán lại. */
    public static final int MAX_ATTEMPTS = 5;
    /** Đơn chưa trả tiền quá hạn này thì không cho thanh toán lại nữa. */
    public static final Duration PAYMENT_WINDOW = Duration.ofHours(24);

    // Trạng thái đơn còn cho phép mở cổng thanh toán.
    static final List<OrderStatus> PAYABLE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);

    PaymentRepository paymentRepository;

    /**
     * Mở một lần thử thanh toán mới cho đơn. Ném lỗi nếu đơn không đủ điều kiện
     * (sai phương thức, đã giao/hủy, đã trả tiền, quá hạn, hoặc quá số lần thử).
     */
    @Transactional
    public Payment startPayment(Order order) {
        assertPayable(order);

        int attemptNo = (int) this.paymentRepository.countByOrderId(order.getId()) + 1;
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAttemptNo(attemptNo);
        payment.setMethod(order.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalPrice());
        payment.setTxnRef(buildTxnRef(order.getOrderCode(), attemptNo));
        return this.paymentRepository.save(payment);
    }

    /**
     * Kiểm tra điều kiện được phép thanh toán (lại) — dùng chung cho cả lần đầu
     * và retry: đúng cổng VNPay, đơn chưa giao/hủy, chưa trả tiền, còn trong hạn
     * giữ đơn và chưa vượt số lần thử.
     *
     * @return null nếu hợp lệ, ngược lại là lỗi giải thích vì sao bị chặn (để FE
     *         hiển thị đúng lý do thay vì chỉ ẩn nút).
     */
    @Transactional(readOnly = true)
    public ErrorCode checkPayable(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
            return ErrorCode.ORDER_NOT_PAYABLE;
        }
        if (!PAYABLE_STATUSES.contains(order.getStatus())) {
            return ErrorCode.ORDER_NOT_PAYABLE;
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return ErrorCode.ORDER_ALREADY_PAID;
        }
        if (order.getOrderDate() != null
                && order.getOrderDate().plus(PAYMENT_WINDOW).isBefore(LocalDateTime.now())) {
            return ErrorCode.ORDER_PAYMENT_EXPIRED;
        }
        if (this.paymentRepository.countByOrderId(order.getId()) >= MAX_ATTEMPTS) {
            return ErrorCode.PAYMENT_TOO_MANY_ATTEMPTS;
        }
        return null;
    }

    /** Như {@link #checkPayable} nhưng ném lỗi — dùng khi thực sự mở cổng. */
    public void assertPayable(Order order) {
        ErrorCode error = checkPayable(order);
        if (error != null) {
            throw new AppException(error);
        }
    }

    /** Tra lần thử theo mã giao dịch cổng gửi về — nguồn xác thực của callback. */
    @Transactional(readOnly = true)
    public Payment findByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        return this.paymentRepository.findByTxnRef(txnRef).orElse(null);
    }

    /**
     * Ghi kết quả callback vào lần thử rồi đồng bộ trạng thái tổng hợp của đơn.
     * Idempotent: lần thử đã có kết quả cuối (PAID/FAILED) thì bỏ qua.
     *
     * @return true nếu vừa cập nhật, false nếu bản ghi đã ở trạng thái cuối.
     */
    @Transactional
    public boolean completePayment(Payment payment, String responseCode, Map<String, String> rawParams) {
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
            return false;
        }

        payment.setResponseCode(responseCode);
        payment.setTransactionNo(rawParams.get("vnp_TransactionNo"));
        payment.setBankCode(rawParams.get("vnp_BankCode"));
        payment.setPayDate(rawParams.get("vnp_PayDate"));
        payment.setTransactionStatus(rawParams.get("vnp_TransactionStatus"));
        payment.setRawResponse(toJsonLike(rawParams));
        payment.setStatus("00".equals(responseCode) ? PaymentStatus.PAID : PaymentStatus.FAILED);
        this.paymentRepository.save(payment);

        syncOrderStatus(payment);
        return true;
    }

    /**
     * Đồng bộ {@code Order.paymentStatus} từ các lần thử. Đơn đã PAID thì giữ
     * nguyên (không để một lần thử hỏng sau đó kéo đơn về FAILED).
     */
    private void syncOrderStatus(Payment payment) {
        Order order = payment.getOrder();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaymentTxnRef(payment.getTxnRef());
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
        }
    }

    /** Lịch sử các lần thử của đơn, mới nhất trước — hiển thị ở trang chi tiết. */
    @Transactional(readOnly = true)
    public List<Payment> getHistory(String orderId) {
        return this.paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId);
    }

    // "LS12345678" + lần 2 → "LS12345678A2". Chỉ chữ và số cho đúng ràng buộc
    // Alphanumeric của VNPay; tra cứu dựa vào cột txnRef nên không cần parse.
    private static String buildTxnRef(String orderCode, int attemptNo) {
        return orderCode + "A" + attemptNo;
    }

    /**
     * Tuần tự hóa tham số callback để lưu thô. Không dùng Jackson vì đây chỉ là
     * chuỗi tra cứu, không phải hợp đồng API.
     */
    private static String toJsonLike(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder("{");
        sorted.forEach((key, value) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(key).append("\":\"").append(value).append('"');
        });
        return sb.append('}').toString();
    }
}
