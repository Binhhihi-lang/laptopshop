package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.dto.response.User.UserResponse;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Bỏ qua các field cần xử lý riêng trong Service:
    // - id: do JPA tự sinh
    // - password: cần mã hóa (BCrypt), không map thẳng chuỗi thô
    // - avatar: cần upload file trước rồi mới có tên file để set
    // - roles: cần lookup từng Role thật từ DB qua roleNames, không map thẳng
    // - email: cần trim().toLowerCase() theo đúng convention hiện tại
    // - deletedAt: field hệ thống, không cho client động vào
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "orders", ignore = true)
    User toEntity(UserCreationRequest request);

    // @MappingTarget: map dữ liệu mới từ DTO ĐÈ LÊN Entity cũ đã có sẵn
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "orders", ignore = true)
    void updateEntity(UserUpdateRequest request, @MappingTarget User entity);

    // Entity -> Response: KHÔNG có field password (an toàn). "roles" (Set<Role>)
    // tự động convert sang "roleNames" (List<String>) nhờ MapStruct phát hiện
    // helper method roleToName(Role) bên dưới và áp dụng cho từng phần tử.
    @Mapping(target = "roleNames", source = "roles")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    // Helper: MapStruct tự nhận ra method này khớp kiểu Role -> String và dùng
    // nó để convert từng phần tử trong Set<Role> -> List<String> ở UserResponse trả
    // về giao diện
    default String roleToName(Role role) {
        return role.getName();
    }
}
