package com.example.laptopshop.config;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

@Configuration
public class ApplicationInitConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationInitConfig.class);

    @Bean
    ApplicationRunner applicationRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            // 1. Kiểm tra & Tạo Role ADMIN nếu chưa có trong DB
            Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
                Role role = new Role();
                role.setName("ADMIN");
                role.setDescription("Quản trị viên hệ thống");
                return roleRepository.save(role);
            });

            // 2. Kiểm tra & Tạo Role USER nếu chưa có trong DB
            Role userRole = roleRepository.findByName("USER").orElseGet(() -> {
                Role role = new Role();
                role.setName("USER");
                role.setDescription("Khách hàng mua sắm");
                return roleRepository.save(role);
            });

            // 3. Kiểm tra xem tài khoản Admin mặc định đã tồn tại chưa (dùng email)
            String adminEmail = "admin@gmail.com";

            if (!userRepository.existsByEmail(adminEmail)) {

                Set<Role> roles = new HashSet<>();
                roles.add(adminRole); // Gán role ADMIN vừa khởi tạo

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