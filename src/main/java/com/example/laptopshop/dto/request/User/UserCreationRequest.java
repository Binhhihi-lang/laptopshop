package com.example.laptopshop.dto.request.User;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

public class UserCreationRequest {

    @NotBlank(message = "USER_EMAIL_EMPTY")
    @Email(message = "INVALID_EMAIL")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "USER_PASSWORD_EMPTY")
    @Size(min = 8, message = "INVALID_PASSWORD")
    private String password;

    private String fullName;
    private String address;
    private String phone;
    private String roleName;

    private MultipartFile inputFile; // Hứng file ảnh avatar trực tiếp trong DTO này luôn!

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAddress() {
        return address;
    }

    public MultipartFile getInputFile() {
        return inputFile;
    }

    public void setInputFile(MultipartFile inputFile) {
        this.inputFile = inputFile;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

}