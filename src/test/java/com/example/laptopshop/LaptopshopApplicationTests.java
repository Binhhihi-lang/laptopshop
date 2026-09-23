package com.example.laptopshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Nghiệm thu nhanh: Spring context dựng được từ đầu tới cuối hay không.
 *
 * <p>
 * {@code @ActiveProfiles("test")} là phần BẮT BUỘC, không phải trang trí. Không
 * có nó, context sẽ nạp {@code application.properties} thật và nối thẳng vào DB
 * Aiven production bằng biến môi trường — {@code ddl-auto} khi đó sửa được cấu
 * trúc bảng của hệ thống đang chạy thật, chỉ từ một lệnh {@code mvn test}. Hồ sơ
 * "test" ({@code application-test.properties}) trỏ sang H2 in-memory nên test
 * không còn khả năng chạm vào production.
 */
@SpringBootTest
@ActiveProfiles("test")
class LaptopshopApplicationTests {

	@Test
	void contextLoads() {
	}

}
