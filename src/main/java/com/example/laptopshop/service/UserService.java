package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.example.laptopshop.domain.Permission;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.CachedAuthorities;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserProfileUpdateRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.UserMapper;
import com.example.laptopshop.repository.CachedAuthoritiesRepository;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@Service
public class UserService {

     UserRepository userRepository;
     PasswordEncoder passwordEncoder;
     RoleRepository roleRepository;
     UploadService uploadService;
     UserMapper userMapper;
     CachedAuthoritiesRepository cachedAuthoritiesRepository;

     // TTL cache quyền (giây). Đủ ngắn để tự làm mới nếu quên evict, đủ dài để
     // gần như mọi request được phục vụ từ Redis (0 query DB).
     private static final long AUTHORITIES_CACHE_TTL = 300;

    public User getUserById(String id) {
        return this.userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    // Dùng cho AuthenticationService lúc đăng nhập -> so khớp password, KHÔNG
    // qua @Where filter là chưa xóa mềm vì đã tự động áp dụng ở tầng Entity,
    public User getUserByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    // Xóa MỀM: gọi y hệt như xóa thật trước đây, nhưng nhờ @SQLDelete khai báo
    // ở User.java, Hibernate tự động đổi câu lệnh thành UPDATE deleted_at =
    // NOW() thay vì DELETE thật
    public void deleteUserById(String id) {
        User user = getUserById(id); // kiểm tra tồn tại, nếu không
        if (user.getAvatar() != null) {
            this.uploadService.handleDeleteFile(user.getAvatar());
        }
        this.userRepository.delete(user);
    }

    // Xóa hàng loạt người dùng theo danh sách id: xóa avatar của từng user
    // trước khi xóa bản ghi (giống deleteUser đơn), wrap trong 1 transaction để
    // các bước xóa ảnh + xóa record nhất quán với nhau. Tương tự
    // deleteUserById, deleteAll() cũng bị @SQLDelete đổi thành xóa MỀM
    // (UPDATE deleted_at).
    @Transactional
    public void deleteUsersByIds(List<String> ids) {
        List<User> users = this.userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        for (User user : users) {
            if (user.getAvatar() != null) {
                this.uploadService.handleDeleteFile(user.getAvatar());
            }
        }
        this.userRepository.deleteAll(users);
    }

    // Kích hoạt/khóa hàng loạt người dùng theo danh sách id
    // @Transactional đủ id thì mới kích hoạt xóa không thì báo lỗi
    @Transactional
    public void updateUsersActive(List<String> ids, boolean active) {
        List<User> users = this.userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        users.forEach(user -> user.setActive(active));
        this.userRepository.saveAll(users);
    }

    public Role getRoleByName(String name) {
        return this.roleRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
    }

    // Build lại danh sách quyền (authorities) từ CHỈ các Role đang ACTIVE của
    // user. Dùng cho CustomJwtAuthenticationConverter để thu hồi quyền ngay khi
    // một Role bị khóa (active=false) hoặc xóa mềm, trên request tiếp theo.
    // @Transactional(readOnly=true) để load được quan hệ @ManyToMany lazy
    // (roles -> permissions) trong cùng 1 session. User không tồn tại (kể cả đã
    // xóa mềm do @SQLRestriction) -> trả List rỗng (bị từ chối toàn bộ).
    //
    // Phương án B (cache Redis): đọc cache trước, chỉ query DB khi cache miss,
    // rồi lưu lại với TTL ngắn. Các thao tác Redis nằm ngoài JPA nên không bị
    // ảnh hưởng bởi readOnly của transaction.
    @Transactional(readOnly = true)
    public List<org.springframework.security.core.GrantedAuthority> getActiveAuthorities(String userId) {
        // 1. Thử lấy từ cache Redis trước (0 query DB nếu hit)
        CachedAuthorities cached = this.cachedAuthoritiesRepository.findById(userId).orElse(null);
        if (cached != null) {
            return cached.getAuthorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }

        // 2. Cache miss -> tính từ DB (logic cũ)
        User user = this.userRepository.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        List<String> authorityNames = new ArrayList<>();
        for (Role role : user.getRoles()) {
            if (!role.isActive()) {
                continue; // Role bị khóa -> thu hồi toàn bộ quyền của role này
            }
            authorityNames.add("ROLE_" + role.getName());
            for (Permission permission : role.getPermissions()) {
                authorityNames.add(permission.getName());
            }
        }

        // 3. Lưu cache 5 phút (TTL tự động làm mới phòng quên evict)
        this.cachedAuthoritiesRepository.save(CachedAuthorities.builder()
                .userId(userId)
                .authorities(authorityNames)
                .ttl(AUTHORITIES_CACHE_TTL)
                .build());

        return authorityNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    // Xóa cache quyền của 1 user (dùng khi user bị đổi role). Lần truy cập sau
    // sẽ load lại DB và cache giá trị mới.
    public void evictUserAuthorities(String userId) {
        this.cachedAuthoritiesRepository.deleteById(userId);
    }

    // Xóa cache quyền của TẤT CẢ user thuộc các role bị đổi/khóa/xóa -> thu hồi
    // ngay trên request tiếp theo (Q2). Gọi trong @Transactional để load được
    // quan hệ lazy role.getUsers().
    public void evictUsersOfRoles(Collection<Role> roles) {
        for (Role role : roles) {
            for (User user : role.getUsers()) {
                this.cachedAuthoritiesRepository.deleteById(user.getId());
            }
        }
    }

    // nhiều Role cùng lúc theo danh sách tên, dùng cho create/update User
    // có nhiều Role. Nếu 1 tên bất kỳ không tồn tại -> ném ROLE_NOT_FOUND
    public Set<Role> getRolesByNames(List<String> names) {
        Set<Role> roles = new HashSet<>();
        for (String name : names) {
            roles.add(getRoleByName(name));
        }
        return roles;
    }

    // Validate
    public void validateEmail(String email, String currentId) {

        // Nếu mà trùng email mà khác Id thì ném lỗi. Nhờ @Where ở User.java,
        // existsByEmailIgnoreCase(...) TỰ ĐỘNG chỉ tính user CHƯA xóa mềm ->
        // email của 1 user đã xóa mềm được coi là "còn trống", có thể đăng ký
        // lại bình thường.
        String normalized = email.trim();
        boolean exists = currentId == null
                ? this.userRepository.existsByEmailIgnoreCase(normalized)
                : this.userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }
    }

    // ---- Các method trả Response DTO: LUÔN @Transactional để Hibernate Session
    // còn mở trong lúc MapStruct đọc user.getRoles() (quan hệ @ManyToMany, lazy),
    // tránh LazyInitializationException ----

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUserResponses() {
        List<User> users = this.userRepository.findAll();
        return this.userMapper.toResponseList(users);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseById(String id) {
        User user = getUserById(id);
        return this.userMapper.toResponse(user);
    }

    // nhận về DTO UserCreationRequest, validate dữ liệu, map sang Entity User, mã
    // hóa mật khẩu, lưu xuống DB rồi map luôn sang Response TRONG CÙNG
    // transaction trước khi trả về Controller.
    @Transactional
    public UserResponse handleCreateUser(UserCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        validateEmail(request.getEmail(), null);

        // 2. Map các field thuần (fullName, phone, address) từ DTO sang Entity qua
        // MapStruct. password/avatar/roles/email KHÔNG được map ở đây (đã ignore
        // trong UserMapper) vì cần xử lý riêng bên dưới.
        User newUser = this.userMapper.toEntity(request);
        newUser.setEmail(request.getEmail().trim());

        // 3. Mã hóa mật khẩu thô
        String hashPassword = this.passwordEncoder.encode(request.getPassword());
        newUser.setPassword(hashPassword);

        // 4. Xử lý lưu File avatar nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String avatarName = this.uploadService.handleSaveUploadFile(file, "avatar");
            newUser.setAvatar(avatarName);
        }

        // set Role
        Set<Role> roles = getRolesByNames(request.getRoleNames());
        newUser.setRoles(roles);

        User saved = this.userRepository.save(newUser);
        this.evictUserAuthorities(saved.getId()); // role mới -> cache sẽ tính lại
        return this.userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse handleUpdateUser(String id, UserUpdateRequest request) {
        // 1. Tìm User cũ trong DB, không thấy thì ném lỗi
        User existingUser = getUserById(id);

        // 2. Validate email mới xem có trùng với ai khác không
        validateEmail(request.getEmail(), id);
        Set<Role> roles = getRolesByNames(request.getRoleNames());

        // 3. Đổ các field thuần (fullName, phone, address) từ DTO đè lên Entity cũ
        // qua MapStruct (@MappingTarget), rồi set riêng email/roles bên dưới
        this.userMapper.updateEntity(request, existingUser);
        existingUser.setEmail(request.getEmail().trim());
        existingUser.setRoles(roles);

        // 4. Xử lý đổi ảnh avatar mới nếu có gửi lên file mới
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String newAvatar = this.uploadService.handleSaveUploadFile(file, "avatar");
            existingUser.setAvatar(newAvatar);
        }

        // 5. Lưu Entity đã cập nhật dữ liệu mới xuống DB
        User saved = this.userRepository.save(existingUser);
        this.evictUserAuthorities(saved.getId()); // role có thể đổi -> cache tính lại
        return this.userMapper.toResponse(saved);
    }

    // Lấy hồ sơ cá nhân của user đang đăng nhập (không cần READ_USER).
    // Dùng cho endpoint /me — bất kỳ tài khoản hợp lệ nào cũng xem được chính mình.
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(String userId) {
        return getUserResponseById(userId);
    }

    // Cập nhật HỒ SƠ CÁ NHÂN của chính user đang đăng nhập (endpoint /me).
    // CHỈ cho phép đổi fullName / phone / address / avatar. KHÔNG đụng đến
    // email, roleNames, active, password — đảm bảo STAFF/user thường không tự
    // nâng quyền hay đổi email của mình. Không evict cache quyền vì quyền không đổi.
    @Transactional
    public UserResponse handleUpdateMyProfile(String userId, UserProfileUpdateRequest request) {
        User existingUser = getUserById(userId);

        if (request.getFullName() != null) {
            existingUser.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            existingUser.setPhone(request.getPhone().trim());
        }
        if (request.getAddress() != null) {
            existingUser.setAddress(request.getAddress().trim());
        }

        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String newAvatar = this.uploadService.handleSaveUploadFile(file, "avatar");
            existingUser.setAvatar(newAvatar);
        }

        User saved = this.userRepository.save(existingUser);
        return this.userMapper.toResponse(saved);
    }

    // Cập nhật lastLoginAt khi user đăng nhập thành công
    @Transactional
    public void updateLastLoginAt(String userId, LocalDateTime lastLoginAt) {
        User user = getUserById(userId);
        user.setLastLoginAt(lastLoginAt);
        this.userRepository.save(user);
    }
}
