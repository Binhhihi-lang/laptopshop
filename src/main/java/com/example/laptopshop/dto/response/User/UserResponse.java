package com.example.laptopshop.dto.response.User;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {

    private String id;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String avatar;
    private List<String> roleNames;

}
