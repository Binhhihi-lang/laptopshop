package com.example.laptopshop.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    // Khóa bí mật để ký/verify JWT (thuật toán đối xứng HS512) -> đọc từ
    // application.properties, KHÔNG hardcode trong code, KHÔNG commit key thật lên
    // git.
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Thời hạn token, đơn vị giây
    @Value("${jwt.valid-duration}")
    private long validDuration;

    public AuthenticationService(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // Đăng nhập: kiểm tra email + password (so khớp bằng BCrypt), đúng thì phát
    // hành JWT
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        User user = this.userService.getUserByEmail(request.getEmail().trim());
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
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
                //
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

    // Introspect: kiểm tra 1 token có đúng chữ ký (do chính SIGNER_KEY này ký) và
    // chưa hết hạn không
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        String token = request.getToken();

        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean verified = signedJWT.verify(verifier);

        IntrospectResponse response = new IntrospectResponse();
        response.setValid(verified && expiryTime.after(new Date()));
        return response;
    }

    // Claim "scope" chứa tên Role (vd "ADMIN") -> JwtAuthenticationConverter bên
    // SecurityConfiguration sẽ map thành quyền "ROLE_ADMIN", khớp với
    // .hasRole("ADMIN")
    private String buildScope(User user) {
        if (user.getRole() != null) {
            return user.getRole().getName();
        }
        return "";
    }
}
