package com.example.laptopshop.domain;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name; // vd: "CREATE_PRODUCT", "DELETE_USER"...
    private String description;

    @ManyToMany(mappedBy = "permissions") // "permissions" = tên biến Set<Permission> bên Role
    @JsonIgnore
    private Set<Role> roles = new HashSet<>();

}