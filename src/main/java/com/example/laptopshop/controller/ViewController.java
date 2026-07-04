package com.example.laptopshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    // "redirect:" được Spring xử lý trực tiếp bằng RedirectView, KHÔNG đi qua
    // ViewResolver (JSP) — nên vẫn chạy tốt dù đã bỏ JSP View Resolver.
    // Dùng để giữ URL gọn (vd "/" thay vì phải nhớ "/client/home.html").

    @GetMapping("/")
    public String getHomePage() {
        return "redirect:/client/home.html";
    }

    @GetMapping("/admin")
    public String getDashboardPage() {
        return "redirect:/admin/dashboard/show.html";
    }

    @GetMapping("/product/{id}")
    public String getProductDetailPage(@PathVariable long id) {
        return "redirect:/client/product-detail.html?id=" + id;
    }

    // ---- Đã XÓA 5 mapping /admin/user/** ----
    // Không cần nữa vì đã có static/admin/user/*.html tự được Spring Boot
    // serve trực tiếp, không cần Controller trung gian.

    // ---- Product: TẠM GIỮ LẠI ----
    // Sẽ xóa khi hoàn thành chuyển Product sang HTML tĩnh (bước kế tiếp).
    // Hiện tại gọi vào các URL này vẫn sẽ lỗi (chưa có ViewResolver xử lý),
    // nhưng để placeholder ở đây cho dễ nhớ còn thiếu gì.

    @GetMapping("/admin/product")
    public String getProductPage() {
        return "admin/product/show";
    }

    @GetMapping("/admin/product/create")
    public String getCreateProductPage() {
        return "admin/product/create";
    }

    @GetMapping("/admin/product/update/{id}")
    public String getUpdateProductPage() {
        return "admin/product/update";
    }

    @GetMapping("/admin/product/delete/{id}")
    public String getDeleteProductPage() {
        return "admin/product/delete";
    }

    @GetMapping("/admin/product/{id}")
    public String getInfoProductPage() {
        return "admin/product/detail";
    }
}