package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // lấy all user
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String email;

    @JsonIgnore
    private String password;

    private String fullName;
    private String address;
    private String phone;

    private String avatar;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", // 1. Tên của bảng trung gian sẽ được tạo trong DB

            joinColumns = @JoinColumn(name = "user_id"),
            // 2. Tên cột trong bảng trung gian trỏ về Khóa chính của Entity HIỆN TẠI (User)

            inverseJoinColumns = @JoinColumn(name = "role_id")
    // 3. Tên cột trong bảng trung gian trỏ về Khóa chính của Entity ĐỐI PHƯƠNG
    // (Role)
    )
    private Set<Role> roles = new HashSet<>();

    private LocalDateTime deletedAt; // null = chưa xóa, có giá trị = đã xóa mềm

    @OneToMany(mappedBy = "user")
    @JsonIgnore
    List<Order> orders;

    @Override
    public String toString() {
        return "User [id=" + id + ", email=" + email + ", password=" + password + ", fullName=" + fullName
                + ", address=" + address + ", phone=" + phone + ", avatar=" + avatar + "]";
    }
}
