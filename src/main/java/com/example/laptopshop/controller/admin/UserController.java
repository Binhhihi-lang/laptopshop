package com.example.laptopshop.controller.admin;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.service.UploadService;
import com.example.laptopshop.service.UserService;

import jakarta.servlet.ServletContext;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class UserController {

    private final UserService userService;
    private final UploadService uploadService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, UploadService uploadService,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.uploadService = uploadService;
        this.passwordEncoder = passwordEncoder;
    }

    // table-user
    @RequestMapping("/admin/user")
    public String getUserPage(Model model) {
        List<User> users = this.userService.getAllUser();
        model.addAttribute("userAll", users);
        return "admin/user/show";
    }

    // get detail infoUser/ View
    @RequestMapping("/admin/user/{id}") // Get
    public String getInfoUserPage(Model model, @PathVariable long id) {
        User userDetail = userService.getUserByID(id);
        model.addAttribute("id", id);
        model.addAttribute("user", userDetail);
        return "admin/user/detail";
    }

    // create user
    @GetMapping("/admin/user/create") // Get
    public String getCreateUserPage(Model model) {
        model.addAttribute("newUser", new User()); // truyen vao rong
        return "admin/user/create";
    }

    @PostMapping("/admin/user/create")
    public String postCreateUser(Model model, @ModelAttribute("newUser") User chubinhUser,
            @RequestParam("inputFile") MultipartFile file) {

        String avatar = this.uploadService.handleSaveUploadFile(file, "Avatar");
        String hashPassword = this.passwordEncoder.encode(chubinhUser.getPassword());
        chubinhUser.setAvatar(avatar);
        chubinhUser.setPassword(hashPassword);
        //
        chubinhUser.setRole(this.userService.getRoleByName(chubinhUser.getRole().getName()));
        this.userService.handleSaveUser(chubinhUser);
        return "redirect:/admin/user";
    }

    // Update Info User
    @RequestMapping("/admin/user/update/{id}")
    public String getUpdateUserPage(Model model, @PathVariable long id) {
        User currentUser = userService.getUserByID(id);
        model.addAttribute("userUpdate", currentUser);
        return "admin/user/update";
    }

    @PostMapping("/admin/user/update")
    public String postUpdateUser(Model model, @ModelAttribute("userUpdate") User userUpdate,
            @RequestParam("inputFile") MultipartFile file) {
        User currentUser = userService.getUserByID(userUpdate.getId());
        String avatarUpdate = this.uploadService.handleSaveUploadFile(file, "Avatar");
        // xóa file cũ
        this.uploadService.handleDeleteFile(currentUser.getAvatar(), "Avatar");
        if (currentUser != null) {
            currentUser.setFullName(userUpdate.getFullName());
            currentUser.setPhone(userUpdate.getPhone());
            currentUser.setAddress(userUpdate.getAddress());
            currentUser.setAvatar(avatarUpdate);
            currentUser.setRole(this.userService.getRoleByName(userUpdate.getRole().getName()));
        }
        // set các giá trị cần sửa thì cho biến currentUser
        this.userService.handleSaveUser(currentUser);

        return "redirect:/admin/user";
    }

    // delete
    @GetMapping("/admin/user/delete/{id}")
    public String getDeleteUserPage(Model model, @PathVariable long id) {
        model.addAttribute("id", id);
        // User user = new User();
        // user.setId(id);
        model.addAttribute("userDelete", new User());
        return "admin/user/delete";
    }

    @PostMapping("/admin/user/delete")
    public String postDeleteUser(Model model, @ModelAttribute("userDelete") User userDelete) {
        this.userService.deleteUserById(userDelete.getId());
        return "redirect:/admin/user";
    }

}
