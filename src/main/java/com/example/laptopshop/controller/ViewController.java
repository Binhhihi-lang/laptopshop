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
        return "redirect:/client/index.html";
    }

    @GetMapping("/admin")
    public String getLoginPage() {
        return "redirect:/admin/dashboard/login.html";
    }

}