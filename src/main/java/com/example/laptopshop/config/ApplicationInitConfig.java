package com.example.laptopshop.config;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

@Configuration
public class ApplicationInitConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationInitConfig.class);

    // PasswordEncoder được định nghĩa ở đây (không phải SecurityConfiguration) để
    // tránh vòng lặp phụ thuộc: SecurityConfiguration cần UserService (để build
    // authorities từ role active), còn UserService cần PasswordEncoder -> nếu
    // PasswordEncoder nằm trong SecurityConfiguration sẽ tạo cycle.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Khởi tạo dữ liệu: seed Role/Permission (DataInitializer) + tạo tài khoản
    // Admin mặc định nếu chưa có.
    @Bean
    ApplicationRunner applicationRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            DataInitializer dataInitializer) {

        return args -> {
            // 1. Seed Permission + ADMIN/STAFF/CUSTOMER (idempotent)
            dataInitializer.init();

            // 2. Tạo tài khoản Admin mặc định nếu chưa có
            String adminEmail = "admin@gmail.com";
            if (!userRepository.existsByEmail(adminEmail)) {
                Role adminRole = roleRepository.findByName("ADMIN")
                        .orElseThrow(() -> new IllegalStateException("Role ADMIN chưa được seed"));

                Set<Role> roles = new HashSet<>();
                roles.add(adminRole);

                User adminUser = new User();
                adminUser.setEmail(adminEmail);
                adminUser.setPassword(passwordEncoder.encode("admin123")); // Mật khẩu mặc định
                adminUser.setFullName("System Admin");
                adminUser.setRoles(roles);

                userRepository.save(adminUser);

                log.warn(">>> [INIT SYSTEM] Tài khoản Admin mặc định đã được tạo!");
                log.warn(">>> Email: {} | Mật khẩu: admin123", adminEmail);
                log.warn(">>> Vui lòng đổi mật khẩu sau khi đăng nhập lần đầu để bảo mật.");
            }
        };
    }
}
