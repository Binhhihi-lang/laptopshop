package com.example.laptopshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@SpringBootApplication // đánh dấu đây là class cấu hình
@EnableJpaAuditing
@EnableAsync // bật @Async cho EmailService.sendPasswordResetEmail
@EnableScheduling // bật job dọn đơn VNPay quá hạn chưa thanh toán
public class LaptopshopApplication {

	public static void main(String[] args) {
		SpringApplication.run(LaptopshopApplication.class, args);
	}

}
