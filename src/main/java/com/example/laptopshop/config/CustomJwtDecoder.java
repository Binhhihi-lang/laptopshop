package com.example.laptopshop.config;

import java.text.ParseException;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.example.laptopshop.repository.InvalidatedTokenRepository;
import com.nimbusds.jwt.SignedJWT;

@Component
public class CustomJwtDecoder implements JwtDecoder {

    @Value("${jwt.signerKey}")
    private String signerKey;

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private NimbusJwtDecoder nimbusJwtDecoder; // khởi tạo lazy 1 lần

    public CustomJwtDecoder(InvalidatedTokenRepository invalidatedTokenRepository) {
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        // 1. Kiểm tra blacklist TRƯỚC khi verify chữ ký -> token đã logout thì
        // từ chối ngay, đỡ tốn verify
        try {
            String jwtId = SignedJWT.parse(token).getJWTClaimsSet().getJWTID();
            if (this.invalidatedTokenRepository.existsById(jwtId)) {
                throw new JwtException("Token đã bị logout");
            }
        } catch (ParseException e) {
            throw new JwtException("Token không hợp lệ");
        }

        // 2. Verify chữ ký + hạn dùng NimbusJwtDecoder
        if (Objects.isNull(this.nimbusJwtDecoder)) {
            SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HS512");
            this.nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKeySpec)
                    .macAlgorithm(MacAlgorithm.HS512)
                    .build();
        }
        return this.nimbusJwtDecoder.decode(token);
    }
}