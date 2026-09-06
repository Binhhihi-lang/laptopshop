package com.example.laptopshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email cho các luồng cần thiết của storefront (hiện tại: quên mật khẩu).
 *
 * <p>Nếu chưa cấu hình SMTP (MAIL_USERNAME / MAIL_PASSWORD rỗng) -> chỉ log
 * nội dung email ra console, KHÔNG throw. Mục đích: app vẫn chạy được khi dev
 * chưa setup Gmail App Password, FE vẫn có thể test luồng bằng token lấy từ log.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Gửi email chứa link reset mật khẩu.
     *
     * @param to       email người nhận
     * @param fullName tên hiển thị
     * @param link     URL đầy đủ (đã có query ?token=...)
     */
    @Async
    public void sendPasswordResetEmail(String to, String fullName, String link) {
        String subject = "[LaptopShop] Đặt lại mật khẩu của bạn";
        String body = """
                Xin chào %s,

                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                Vui lòng bấm vào liên kết bên dưới để đặt mật khẩu mới (liên kết có hiệu lực trong 1 giờ):

                %s

                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.

                Trân trọng,
                LaptopShop
                """.formatted(fullName != null ? fullName : to, link);

        sendMail(to, subject, body);
    }

    private void sendMail(String to, String subject, String body) {
        if (mailFrom == null || mailFrom.isBlank()) {
            // Chưa cấu hình SMTP -> log nội dung thay vì gửi. FE/dev có thể test
            // luồng quên mật khẩu bằng cách copy link từ log.
            log.warn("[EmailService] SMTP chua cau hinh (MAIL_USERNAME rong). "
                    + "Bo qua gui mail den={}, subject={}, body={}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            this.mailSender.send(message);
            log.info("[EmailService] Da gui mail den={}, subject={}", to, subject);
        } catch (MailException ex) {
            // Không để lỗi gửi mail phá vỡ luồng đăng ký/đặt lại mật khẩu.
            log.error("[EmailService] Loi gui mail den={}, subject={}", to, subject, ex);
        }
    }
}
