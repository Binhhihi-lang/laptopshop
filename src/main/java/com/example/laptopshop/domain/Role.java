package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "roles")
@SQLDelete(sql = "UPDATE roles SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // lấy all role chưa xóa mềm
@EntityListeners(AuditingEntityListener.class) // BẮT BUỘC để @CreatedDate/@LastModifiedDate được ghi
@Getter
@Setter
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    private String description;

    private boolean active = true; // true: đang dùng, false: bị khóa (thu hồi quyền)

    @CreatedDate
    @Column(updatable = false) // Không bao giờ cho phép UPDATE cột này
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt; // Tự động ghi nhận mỗi khi UPDATE

    private LocalDateTime deletedAt; // null = chưa xóa, có giá trị = đã xóa mềm

    @ManyToMany(mappedBy = "roles") // "roles" là tên biến private Set<Role> roles bên class User
    @JsonIgnore
    private Set<User> users = new HashSet<>();

    // permission
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "Role [id=" + id + ", name=" + name + ", description=" + description + "]";
    }

}
