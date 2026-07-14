package com.example.laptopshop.dto.request.User;

import org.springframework.web.multipart.MultipartFile;

public class UserUpdateRequest {
    private String email; // Dùng để validate trùng lặp nếu họ muốn đổi email
    private String fullName;
    private String phone;
    private String address;
    private String roleName;
    private MultipartFile inputFile; // Nhận ảnh mới nếu họ muốn đổi avatar

    public UserUpdateRequest() {
    }

    // Getter và Setter thuần
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public MultipartFile getInputFile() {
        return inputFile;
    }

    public void setInputFile(MultipartFile inputFile) {
        this.inputFile = inputFile;
    }
}