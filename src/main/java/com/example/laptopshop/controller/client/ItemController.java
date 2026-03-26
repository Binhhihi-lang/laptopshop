package com.example.laptopshop.controller.client;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import ch.qos.logback.core.model.Model;

@Controller
public class ItemController {
    @GetMapping("/product/{id}")
    public String getProductDetail(Model model, @PathVariable long id) {

        return "client/product/detail";
    }
}
