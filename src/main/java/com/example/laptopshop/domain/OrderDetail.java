package com.example.laptopshop.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_detail")
@Getter
@Setter
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private long quantity;
    private double price; // giá tại thời điểm mua (giữ nguyên dù giá sản phẩm sau này đổi)
    private String productCode; // mã sản phẩm tại thời điểm mua
    private String productName; // tên sản phẩm tại thời điểm mua
    private String productImage; // ảnh sản phẩm tại thời điểm mua

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

}
