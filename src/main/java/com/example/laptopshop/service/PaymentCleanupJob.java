package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.repository.OrderRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Dọn đơn VNPay bỏ dở.
 *
 * <p>Tồn kho bị trừ ngay khi tạo đơn, còn {@code vnp_ExpireDate} chỉ có giá trị
 * bên phía VNPay — nếu không tự hủy thì hàng của đơn khách bỏ ngang sẽ bị khóa
 * vĩnh viễn. Job này quét định kỳ, hủy đơn và hoàn kho.
 *
 * <p>Chỉ đụng tới đơn VNPay chưa trả tiền và chưa giao. Đơn COD không bị ảnh
 * hưởng (khách trả tiền mặt khi nhận hàng).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentCleanupJob {

    /** Chạy mỗi giờ; đơn quá hạn giữ hàng thì hủy. */
    static final String CRON_HOURLY = "0 0 * * * *";

    OrderRepository orderRepository;
    OrderService orderService;

    @Scheduled(cron = CRON_HOURLY)
    @Transactional
    public void cancelExpiredVnpayOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(PaymentService.PAYMENT_WINDOW);
        List<Order> expired = this.orderRepository
                .findByPaymentMethodAndPaymentStatusInAndStatusInAndOrderDateBefore(
                        PaymentMethod.VNPAY,
                        EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
                        EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                        cutoff);
        if (expired.isEmpty()) {
            return;
        }

        for (Order order : expired) {
            this.orderService.cancelExpiredOrder(order);
        }
        log.info("Đã hủy {} đơn VNPay quá hạn thanh toán và hoàn lại tồn kho", expired.size());
    }
}
