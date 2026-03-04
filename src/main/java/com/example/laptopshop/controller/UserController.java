package com.example.laptopshop.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequestMapping("/")
    public String getHomePage(Model model) {
        List<User> arrUser = this.userService.getAllUserByEmail("1@gmail.com");
        System.out.println(arrUser);
        String test = this.userService.handleHello();
        model.addAttribute("test", test);

        return "hello"; // tìm đến đường dẫn view
    }

    // table-user
    @RequestMapping("/admin/user")
    public String getUserPage(Model model) {
        List<User> users = this.userService.getAllUser();
        model.addAttribute("userAll", users);
        return "admin/user/table-user";
    }

    // get detail infoUser/ View
    @RequestMapping("/admin/user/{id}") // Get
    public String getInfoUserPage(Model model, @PathVariable long id) {
        User userDetail = userService.getUserByID(id);
        model.addAttribute("id", id);
        model.addAttribute("user", userDetail);
        return "admin/user/show";
    }

    // create user
    @RequestMapping("/admin/user/create") // Get
    public String getCreateUserPage(Model model) {
        model.addAttribute("newUser", new User()); // truyen vao rong
        return "admin/user/create";
    }

    @RequestMapping(value = "/admin/user/create", method = RequestMethod.POST)
    public String postCreateUser(Model model, @ModelAttribute("newUser") User chubinhUser) {
        System.out.println("run here" + chubinhUser);
        userService.handleSaveUser(chubinhUser);
        return "redirect:/admin/user";
    }

    // Update Info User
    @RequestMapping("/admin/user/update/{id}")
    public String getUpdateUserPage(Model model, @PathVariable long id) {
        User currentUser = userService.getUserByID(id);
        model.addAttribute("userUpdate", currentUser);
        return "admin/user/updateView";
    }

    @PostMapping("/admin/user/update")
    public String postUpdateUser(Model model, @ModelAttribute("userUpdate") User userUpdate) {
        User currentUser = userService.getUserByID(userUpdate.getId());
        if (currentUser != null) {
            currentUser.setFullName(userUpdate.getFullName());
            currentUser.setPhone(userUpdate.getPhone());
            currentUser.setAddress(userUpdate.getAddress());
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
