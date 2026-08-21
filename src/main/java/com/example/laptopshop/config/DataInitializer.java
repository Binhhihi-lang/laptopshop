package com.example.laptopshop.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Permission;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.repository.PermissionRepository;
import com.example.laptopshop.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seed dữ liệu khởi tạo: toàn bộ Permission (taxonomy Module+action) và 3 vai
 * trò ADMIN / STAFF / CUSTOMER với đúng tập quyền. Chạy idempotent (tìm theo
 * tên, thiếu mới tạo) nên an toàn khi chạy lại nhiều lần.
 *
 * <p>Quan trọng: method {@code init()} được đánh dấu {@code @Transactional} để
 * các entity Role/Permission là managed. Khi gán quyền cho role ta mutate
 * collection hiện tại bằng {@code clear()} + {@code addAll()} thay vì thay thế
 * bằng {@code new HashSet<>()} — tránh lỗi duplicate key trên bảng trung gian
 * role_permissions khi chạy lại trên DB đã có dữ liệu.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    // Taxonomy quyền: Module + action. ADMIN = ALL; STAFF = CREATE/READ/UPDATE
    // cho PRODUCT/CATEGORY/COUPON + CREATE/READ/UPDATE_ORDER + READ_DASHBOARD
    // (KHÔNG DELETE_*, KHÔNG USER, KHÔNG MANAGE_ROLES_PERMISSIONS); CUSTOMER =
    // không có quyền /admin nào.
    private static final Map<String, String> PERMISSION_SEED = new LinkedHashMap<>();
    static {
        PERMISSION_SEED.put("CREATE_PRODUCT", "Tạo sản phẩm");
        PERMISSION_SEED.put("READ_PRODUCT", "Xem sản phẩm");
        PERMISSION_SEED.put("UPDATE_PRODUCT", "Cập nhật sản phẩm");
        PERMISSION_SEED.put("DELETE_PRODUCT", "Xóa sản phẩm");
        PERMISSION_SEED.put("CREATE_CATEGORY", "Tạo danh mục");
        PERMISSION_SEED.put("READ_CATEGORY", "Xem danh mục");
        PERMISSION_SEED.put("UPDATE_CATEGORY", "Cập nhật danh mục");
        PERMISSION_SEED.put("DELETE_CATEGORY", "Xóa danh mục");
        PERMISSION_SEED.put("CREATE_COUPON", "Tạo mã giảm giá");
        PERMISSION_SEED.put("READ_COUPON", "Xem mã giảm giá");
        PERMISSION_SEED.put("UPDATE_COUPON", "Cập nhật mã giảm giá");
        PERMISSION_SEED.put("DELETE_COUPON", "Xóa mã giảm giá");
        // ORDER: KHÔNG có DELETE_ORDER (Order là bản ghi giao dịch)
        PERMISSION_SEED.put("CREATE_ORDER", "Tạo đơn hàng");
        PERMISSION_SEED.put("READ_ORDER", "Xem đơn hàng");
        PERMISSION_SEED.put("UPDATE_ORDER", "Cập nhật trạng thái đơn hàng");
        PERMISSION_SEED.put("CREATE_USER", "Tạo người dùng");
        PERMISSION_SEED.put("READ_USER", "Xem người dùng");
        PERMISSION_SEED.put("UPDATE_USER", "Cập nhật người dùng");
        PERMISSION_SEED.put("DELETE_USER", "Xóa người dùng");
        PERMISSION_SEED.put("MANAGE_ROLES_PERMISSIONS", "Quản lý vai trò và quyền hạn");
        PERMISSION_SEED.put("READ_DASHBOARD", "Xem bảng điều khiển thống kê");
    }

    @Transactional
    public void init() {
        // 1. Seed Permission (idempotent)
        Map<String, Permission> permissionCache = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : PERMISSION_SEED.entrySet()) {
            String name = entry.getKey();
            Permission permission = permissionRepository.findByName(name).orElseGet(() -> {
                Permission p = new Permission();
                p.setName(name);
                p.setDescription(entry.getValue());
                return permissionRepository.save(p);
            });
            permissionCache.put(name, permission);
        }

        // 2. Hàm phụ: gom Set<Permission> từ danh sách tên
        java.util.function.Function<List<String>, Set<Permission>> toPermSet = names -> {
            Set<Permission> set = new HashSet<>();
            for (String n : names) {
                Permission p = permissionCache.get(n);
                if (p != null) {
                    set.add(p);
                }
            }
            return set;
        };

        List<String> productCatCouponCrud = new ArrayList<>();
        for (String module : new String[] { "PRODUCT", "CATEGORY", "COUPON" }) {
            productCatCouponCrud.add("CREATE_" + module);
            productCatCouponCrud.add("READ_" + module);
            productCatCouponCrud.add("UPDATE_" + module);
        }

        // 3. Seed ADMIN (toàn quyền)
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setName("ADMIN");
            role.setDescription("Quản trị viên hệ thống");
            return roleRepository.save(role);
        });
        adminRole.setActive(true);
        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(new HashSet<>(permissionCache.values()));
        adminRole = roleRepository.save(adminRole);

        // 4. Seed STAFF (nhân viên - quyền hạn chế)
        Role staffRole = roleRepository.findByName("STAFF").orElseGet(() -> {
            Role role = new Role();
            role.setName("STAFF");
            role.setDescription("Nhân viên quản lý sản phẩm, danh mục, mã giảm giá và đơn hàng");
            return roleRepository.save(role);
        });
        List<String> staffPerms = new ArrayList<>(productCatCouponCrud);
        staffPerms.add("CREATE_ORDER");
        staffPerms.add("READ_ORDER");
        staffPerms.add("UPDATE_ORDER");
        staffPerms.add("READ_DASHBOARD");

//        staffPerms.add("CREATE_USER");
//        staffPerms.add("READ_USER");
//        staffPerms.add("UPDATE_USER");
        staffRole.setActive(true);
        staffRole.getPermissions().clear();
        staffRole.getPermissions().addAll(toPermSet.apply(staffPerms));
        staffRole = roleRepository.save(staffRole);

        // 5. Seed CUSTOMER (khách hàng - không có quyền /admin)
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role role = new Role();
            role.setName("CUSTOMER");
            role.setDescription("Khách hàng mua sắm");
            return roleRepository.save(role);
        });
        customerRole.setActive(true);
        customerRole.getPermissions().clear();
        customerRole.getPermissions().addAll(new HashSet<>());
        customerRole = roleRepository.save(customerRole);

        log.info(">>> [INIT SYSTEM] Seed ADMIN/STAFF/CUSTOMER + {} permission thành công.",
                permissionCache.size());
    }
}
