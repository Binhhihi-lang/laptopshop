package com.example.laptopshop.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;


import com.example.laptopshop.domain.InvalidatedToken;
import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.repository.InvalidatedTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Auth.AuthenticationRequest;
import com.example.laptopshop.dto.request.Auth.IntrospectRequest;
import com.example.laptopshop.dto.response.AuthenticationResponse;
import com.example.laptopshop.dto.response.IntrospectResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final InvalidatedTokenRepository invalidatedTokenRepository;


    // Khóa bí mật để ký/verify JWT (thuật toán đối xứng HS512) -> đọc từ
    // application.properties, KHÔNG hardcode trong code, KHÔNG commit key thật lên
    // git.
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Thời hạn token, đơn vị giây
    @Value("${jwt.valid-duration}")
    private long validDuration;

    public AuthenticationService(UserService userService, PasswordEncoder passwordEncoder, InvalidatedTokenRepository invalidatedTokenRepository) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    // Đăng nhập: kiểm tra email + password (so khớp bằng BCrypt), đúng thì phát
    // hành JWT
    @Transactional(readOnly = true)
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // UserService.getUserByEmail trả về User đơn (null nếu không tìm thấy),
        // khác bản trước đây trả List<User>.
        User user = this.userService.getUserByEmail(request.getEmail().trim().toLowerCase());
        if (user == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean authenticated = this.passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String token = generateToken(user);

        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken(token);
        response.setAuthenticated(true);
        return response;
    }

    // Verify chữ ký + hạn dùng + KHÔNG nằm trong blacklist Redis -> dùng chung
    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        if (this.invalidatedTokenRepository.existsById(jwtId)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED); // token đã bị logout trước đó
        }

        return signedJWT;
    }

    // Introspect: kiểm tra 1 token có đúng chữ ký
    // introspect() sửa lại: dùng verifyToken() thay vì tự verify tay như cũ
    public IntrospectResponse introspect(IntrospectRequest request) {
        boolean valid = true;
        try {
            verifyToken(request.getToken());
        } catch (AppException | JOSEException | ParseException e) {
            valid = false;
        }
        IntrospectResponse response = new IntrospectResponse();
        response.setValid(valid);
        return response;
    }

    // Logout: chỉ lưu jwtId
    // vào Redis kèm TTL = thời gian còn lại tới lúc hết hạn
    public void logout(LogoutRequest request) {
        try {
            SignedJWT signedJWT = verifyToken(request.getToken());

            String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

            long ttl = (expiryTime.getTime() - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                this.invalidatedTokenRepository.save(new InvalidatedToken(jwtId, ttl));
            }
        } catch (AppException | JOSEException | ParseException e) {
            // Token không hợp lệ / đã hết hạn / đã logout từ trước -> coi như
            // logout thành công luôn, không cần ném lỗi lại cho client
        }
    }
    // Tạo JWT: Header (thuật toán HS512) + Payload (thông tin user) rồi ký bằng
    // SIGNER_KEY
    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getFullName())
                .issuer("laptopshop.com")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(validDuration, ChronoUnit.SECONDS)))
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getId())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    // Claim "scope" chứa các tên Role cách nhau bởi khoảng trắng (vd "ADMIN
    // USER") -> JwtGrantedAuthoritiesConverter bên SecurityConfiguration TỰ
    // ĐỘNG split chuỗi theo khoảng trắng thành nhiều quyền "ROLE_ADMIN",
    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            user.getRoles().forEach(role -> {
                // 1. Thêm Role với tiền tố ROLE_
                stringJoiner.add("ROLE_" + role.getName());

                // 2. Thêm tất cả Permission thuộc Role này (KHÔNG có tiền tố ROLE_)
                if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                    role.getPermissions().forEach(permission -> {
                        stringJoiner.add(permission.getName());
                    });
                }
            });
        }

        return stringJoiner.toString();
    }
}