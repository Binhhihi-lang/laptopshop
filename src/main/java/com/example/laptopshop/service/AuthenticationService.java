package com.example.laptopshop.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;


import com.example.laptopshop.domain.InvalidatedToken;
import com.example.laptopshop.domain.RefreshToken;
import com.example.laptopshop.domain.RevokeTicket;
import com.example.laptopshop.domain.UserDeviceSession;
import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.dto.response.DeviceInfoResponse;
import com.example.laptopshop.dto.response.DeviceLimitResponse;
import com.example.laptopshop.exception.DeviceLimitExceededException;
import com.example.laptopshop.repository.InvalidatedTokenRepository;
import com.example.laptopshop.repository.RefreshTokenRepository;
import com.example.laptopshop.repository.RevokeTicketRepository;
import com.example.laptopshop.utils.DeviceNameParser;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceSessionService deviceSessionService;
    private final RevokeTicketRepository revokeTicketRepository;


    // Khóa bí mật để ký/verify JWT (thuật toán đối xứng HS512) -> đọc từ
    // application.properties, KHÔNG hardcode trong code, KHÔNG commit key thật lên
    // git.
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Thời hạn token, đơn vị giây
    @Value("${jwt.valid-duration}")
    private long validDuration;

    // Thời hạn refresh token
    @Value("${jwt.refreshable-duration}")
    protected long refreshableDuration;

    // Hạn dùng của revoke ticket (giây)
    @Value("${app.device.revoke-ticket-ttl-seconds}")
    private long revokeTicketTtlSeconds;


    public AuthenticationService(UserService userService, PasswordEncoder passwordEncoder, InvalidatedTokenRepository invalidatedTokenRepository, RefreshTokenRepository refreshTokenRepository, DeviceSessionService deviceSessionService, RevokeTicketRepository revokeTicketRepository) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceSessionService = deviceSessionService;
        this.revokeTicketRepository = revokeTicketRepository;
    }

    // ================== AUTHENTICATE ==================

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Không có thông tin thiết bị (vd client cũ, hoặc gọi nội bộ) -> bỏ qua
        // toàn bộ logic giới hạn, giữ nguyên hành vi trước đây.
        return authenticate(request, null, null, null);
    }

    /**
     * Đăng nhập kèm nhận diện thiết bị.
     *
     * <p>Giới hạn số thiết bị CHỈ áp cho CUSTOMER — ADMIN/STAFF được miễn (vẫn
     * ghi phiên để hiện trong trang quản lý thiết bị, nhưng không bị chặn).
     *
     * @param deviceId  UUID do FE sinh, gửi qua header {@code X-Device-Id}
     * @param userAgent header User-Agent, dùng để hiển thị tên thiết bị
     * @param ipAddress IP client, dùng để hiển thị
     */
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request, String deviceId,
            String userAgent, String ipAddress) {
        User user = this.userService.getUserByEmail(request.getEmail().trim().toLowerCase());
        if (user == null) {
            log.warn("Dang nhap that bai: khong tim thay user voi email={}", request.getEmail());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean authenticated = this.passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            log.warn("Dang nhap that bai: sai mat khau. userId={}", user.getId());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Kiểm tra tài khoản có bị khóa (active = false) không
        if (!user.isActive()) {
            log.warn("Dang nhap that bai: tai khoan da bi khoa. userId={}", user.getId());
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // Chỉ kiểm tra giới hạn khi biết thiết bị VÀ user không phải quản trị.
        if (deviceId != null && !deviceId.isBlank() && !isPrivileged(user)) {
            enforceDeviceLimit(user, deviceId, userAgent, ipAddress);
        }

        // Ghi phiên thiết bị (ADMIN/STAFF cũng ghi để hiện ở trang quản lý).
        if (deviceId != null && !deviceId.isBlank()) {
            this.deviceSessionService.registerOrReplace(user.getId(), deviceId,
                    DeviceNameParser.parse(userAgent), ipAddress, this.refreshableDuration);

            // Đảm bảo "1 thiết bị = 1 phiên" ở tầng TOKEN, không chỉ ở tầng đếm
            // slot: xóa refresh token của lần đăng nhập trước trên cùng máy, nếu
            // không sẽ tồn tại nhiều refresh token hợp lệ song song (2 tab, hoặc
            // login lại sau khi xóa localStorage).
            this.deviceSessionService.clearRefreshTokensOfDevice(user.getId(), deviceId);
        }

        // Cập nhật lastLoginAt sau khi xác thực thành công
        this.userService.updateLastLoginAt(user.getId(), LocalDateTime.now());

        try {
            return issueTokenPair(user, deviceId);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Ném {@link DeviceLimitExceededException} nếu user đã đủ thiết bị và đây là
     * thiết bị MỚI. Login lại trên thiết bị đã có thì cho qua (ghi đè phiên cũ).
     */
    private void enforceDeviceLimit(User user, String deviceId, String userAgent, String ipAddress) {
        List<UserDeviceSession> sessions = this.deviceSessionService.listSessions(user.getId());

        boolean alreadyKnown = sessions.stream()
                .anyMatch(s -> deviceId.equals(s.getDeviceId()));
        if (alreadyKnown) {
            return; // thiết bị cũ -> thay thế phiên, không chiếm thêm slot
        }

        if (sessions.size() < this.deviceSessionService.getMaxSessions()) {
            return; // còn slot
        }

        log.warn("Vuot gioi han thiet bi. userId={}, soThietBi={}", user.getId(), sessions.size());

        // Cấp vé tạm để FE gọi "đăng xuất thiết bị cũ nhất" mà không cần gửi lại
        // mật khẩu (mật khẩu đã được verify ở trên rồi).
        String ticket = UUID.randomUUID().toString();
        this.revokeTicketRepository.save(RevokeTicket.builder()
                .id(ticket)
                .userId(user.getId())
                .deviceId(deviceId)
                .ttl(this.revokeTicketTtlSeconds)
                .build());

        throw new DeviceLimitExceededException(DeviceLimitResponse.builder()
                .devices(toDeviceInfoList(sessions, deviceId))
                .revokeTicket(ticket)
                .maxSessions(this.deviceSessionService.getMaxSessions())
                .build());
    }

    /** ADMIN/STAFF không bị giới hạn thiết bị. */
    private boolean isPrivileged(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(role -> {
            String name = role.getName();
            return "ADMIN".equals(name) || "STAFF".equals(name);
        });
    }

    /** Map phiên -> DTO hiển thị, sắp xếp thiết bị hoạt động gần nhất lên đầu. */
    private List<DeviceInfoResponse> toDeviceInfoList(List<UserDeviceSession> sessions, String currentDeviceId) {
        List<DeviceInfoResponse> result = new ArrayList<>();
        for (UserDeviceSession session : sessions) {
            result.add(DeviceInfoResponse.builder()
                    .deviceId(session.getDeviceId())
                    .deviceName(session.getDeviceName())
                    .ipAddress(session.getIpAddress())
                    .createdAt(session.getCreatedAt())
                    .lastActiveAt(session.getLastActiveAt())
                    .current(session.getDeviceId().equals(currentDeviceId))
                    .build());
        }
        result.sort((a, b) -> {
            if (a.getLastActiveAt() == null) return 1;
            if (b.getLastActiveAt() == null) return -1;
            return b.getLastActiveAt().compareTo(a.getLastActiveAt());
        });
        return result;
    }

    // ================== ĐĂNG XUẤT THIẾT BỊ ĐÃ CHỌN ==================

    /**
     * Xác thực bằng revoke ticket (vé cấp kèm lỗi 1013), đá thiết bị mà user
     * CHỌN rồi cấp token cho thiết bị đang xin đăng nhập — 1 round-trip.
     *
     * <p>Vé bị xóa NGAY khi dùng (one-time-use) nên không thể replay. Vé gắn với
     * thiết bị ĐANG xin đăng nhập, nên kẻ có vé cũng không đăng nhập được ở máy
     * khác.
     */
    @Transactional
    public AuthenticationResponse revokeDeviceAndLogin(String ticket, String targetDeviceId) {
        RevokeTicket revokeTicket = this.revokeTicketRepository.findById(ticket)
                .orElseThrow(() -> new AppException(ErrorCode.REVOKE_TICKET_INVALID));

        // Xóa trước khi làm bất cứ việc gì -> đảm bảo dùng 1 lần.
        this.revokeTicketRepository.deleteById(ticket);

        String userId = revokeTicket.getUserId();
        User user = this.userService.getUserById(userId);
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // Không cho đá chính thiết bị đang xin đăng nhập -> vô nghĩa và dễ gây
        // trạng thái không mong đợi.
        String newDeviceId = revokeTicket.getDeviceId();
        if (targetDeviceId.equals(newDeviceId)) {
            throw new AppException(ErrorCode.DEVICE_SESSION_NOT_FOUND);
        }

        revokeDevice(userId, targetDeviceId);

        try {
            return issueTokenPair(user, newDeviceId);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // ================== ĐĂNG XUẤT THIẾT BỊ KHÁC ==================

    /** Đá mọi thiết bị TRỪ thiết bị đang gửi request (user đã đăng nhập). */
    public void revokeOtherDevices(String userId, String currentDeviceId) {
        this.deviceSessionService.revokeAllExcept(userId, currentDeviceId);
    }

    /** Đá 1 thiết bị cụ thể (user đã đăng nhập, từ trang quản lý thiết bị). */
    public void revokeDevice(String userId, String deviceId) {
        boolean exists = this.deviceSessionService.listSessions(userId).stream()
                .anyMatch(s -> s.getDeviceId().equals(deviceId));
        if (!exists) {
            throw new AppException(ErrorCode.DEVICE_SESSION_NOT_FOUND);
        }
        this.deviceSessionService.revokeSession(userId, deviceId);
    }

    /** Danh sách thiết bị đang đăng nhập của user (trang quản lý thiết bị). */
    public List<DeviceInfoResponse> listDevices(String userId, String currentDeviceId) {
        return toDeviceInfoList(this.deviceSessionService.listSessions(userId), currentDeviceId);
    }

    // ================== INTROSPECT ==================

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

    // ================== LOGOUT ==================

    public void logout(LogoutRequest request) {
        // 1. Blacklist access token (nếu còn verify được)
        try {
            SignedJWT signedJWT = verifyToken(request.getToken());
            String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            long ttl = (expiryTime.getTime() - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                this.invalidatedTokenRepository.save(new InvalidatedToken(jwtId, ttl));
            }
        } catch (AppException | JOSEException | ParseException e) {
            log.warn("Logout: access token khong hop le/da het han, bo qua buoc blacklist.");
        }

        // 2. Thu hồi refresh token (xóa khỏi whitelist Redis) + phiên thiết bị.
        // Xóa phiên là BẮT BUỘC: nếu không, slot thiết bị không được giải phóng
        // và user sẽ bị chặn khi đăng nhập ở máy khác.
        if (request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                SignedJWT signedJWT = SignedJWT.parse(request.getRefreshToken());
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                String refreshJwtId = claims.getJWTID();
                this.refreshTokenRepository.deleteById(refreshJwtId);

                String userId = claims.getStringClaim("userId");
                String deviceId = claims.getStringClaim("deviceId");
                if (userId != null && deviceId != null) {
                    this.deviceSessionService.revokeSession(userId, deviceId);
                }
            } catch (ParseException e) {
                log.warn("Logout: refresh token khong hop le, bo qua buoc thu hoi.");
            }
        }
    }

    // ================== REFRESH TOKEN ==================

    @Transactional(readOnly = true)
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        try {
            SignedJWT signedJWT = verifyRefreshToken(request.getRefreshToken());
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            String oldJwtId = claims.getJWTID();
            String userId = claims.getStringClaim("userId");
            // PHẢI mang deviceId sang token mới, nếu không phiên mất định danh
            // và user bị đá oan ở lần kiểm tra session kế tiếp.
            String deviceId = claims.getStringClaim("deviceId");

            // Giữ nguyên hạn tuyệt đối của refresh token gốc -> phiên đăng nhập
            // có thời gian cố định, không bị refresh liên tục để kéo dài vô hạn
            Date absoluteExpiry = claims.getExpirationTime();

            // Xoay vòng: refresh token cũ dùng 1 lần rồi xóa ngay, chống replay
            this.refreshTokenRepository.deleteById(oldJwtId);

            User user = this.userService.getUserById(userId);

            // PA2: chặn cấp lại token cho tài khoản đã bị KHÓA sau khi login.
            // Không có check này, phiên sống mãi nhờ refresh đến khi refresh
            // token hết hạn tuyệt đối (10 ngày) mới bị đá ra.
            if (!user.isActive()) {
                log.warn("Refresh token tu choi: tai khoan da bi khoa. userId={}", userId);
                throw new AppException(ErrorCode.USER_INACTIVE);
            }

            // Phiên thiết bị đã bị thu hồi (user đá máy này / khóa tài khoản) ->
            // không cấp lại token.
            if (deviceId != null && !deviceId.isBlank()
                    && !this.deviceSessionService.isSessionAlive(userId, deviceId)) {
                log.warn("Refresh token tu choi: phien thiet bi da bi thu hoi. userId={}, deviceId={}",
                        userId, deviceId);
                throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }

            String newAccessToken = generateToken(user, false, deviceId);
            String newRefreshToken = generateRefreshTokenWithExpiry(user, absoluteExpiry, deviceId);
            saveRefreshToken(user.getId(), newRefreshToken, deviceId);
            if (deviceId != null && !deviceId.isBlank()) {
                this.deviceSessionService.touch(userId, deviceId);
            }

            AuthenticationResponse response = new AuthenticationResponse();
            response.setToken(newAccessToken);
            response.setRefreshToken(newRefreshToken);
            response.setAuthenticated(true);
            return response;
        } catch (JOSEException | ParseException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    // Thu hồi TOÀN BỘ refresh token của 1 user -> đá mọi thiết bị khác về
    // login lại. Gọi ngay sau khi đổi mật khẩu thành công.
    // Xóa luôn phiên thiết bị để giải phóng slot đăng nhập.
    public void revokeAllRefreshTokens(String userId) {
        var tokens = this.refreshTokenRepository.findByUserId(userId);
        this.refreshTokenRepository.deleteAll(tokens);
        this.deviceSessionService.revokeAllSessions(userId);
        log.info("Da thu hoi {} refresh token cua userId={}", tokens.size(), userId);
    }

    // Thu hồi TOÀN BỘ refresh token của nhiều user (dùng khi khóa Role):
    // chặn việc tái đăng nhập không cần mật khẩu bằng refresh token đã lưu, kể cả
    // khi role sau đó được kích hoạt trở lại.
    public void revokeRefreshTokensOfUsers(Collection<User> users) {
        for (User user : users) {
            this.revokeAllRefreshTokens(user.getId());
        }
    }

    // ================== TẠO TOKEN ==================

    // Dùng chung cho access token (isRefresh=false) và refresh token (isRefresh=true)
    private String generateToken(User user, boolean isRefresh, String deviceId) {
        Date now = new Date();
        long duration = isRefresh ? refreshableDuration : validDuration;
        Date expirationTime = new Date(now.getTime() + duration * 1000);
        return buildAndSignToken(user, now, expirationTime, deviceId);
    }

    // Dùng lúc refresh: refresh token mới nhưng GIỮ NGUYÊN hạn hết hiệu lực
    private String generateRefreshTokenWithExpiry(User user, Date expirationTime, String deviceId) {
        return buildAndSignToken(user, new Date(), expirationTime, deviceId);
    }

    private String buildAndSignToken(User user, Date issueTime, Date expirationTime, String deviceId) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(user.getFullName())
                .issuer("laptopshop.com")
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getId())
                .claim("scope", buildScope(user));

        // Nối token với PHIÊN THIẾT BỊ: nhờ claim này, khi phiên bị thu hồi thì
        // access token còn hạn vẫn bị chặn ngay ở CustomJwtDecoder (tra Redis).
        // Token cũ không có claim này -> fail-open, không bị đá oan khi deploy.
        if (deviceId != null && !deviceId.isBlank()) {
            claimsBuilder.claim("deviceId", deviceId);
        }

        Payload payload = new Payload(claimsBuilder.build().toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    // Tạo cả cặp accessToken + refreshToken, lưu refreshToken vào Redis (whitelist)
    private AuthenticationResponse issueTokenPair(User user, String deviceId) throws ParseException {
        String accessToken = generateToken(user, false, deviceId);
        String refreshToken = generateToken(user, true, deviceId);
        saveRefreshToken(user.getId(), refreshToken, deviceId);

        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setAuthenticated(true);
        return response;
    }

    private void saveRefreshToken(String userId, String refreshToken, String deviceId) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(refreshToken);
        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        long ttl = (expiryTime.getTime() - System.currentTimeMillis()) / 1000;

        this.refreshTokenRepository.save(new RefreshToken(jwtId, userId, deviceId, ttl));
    }

    // ================== VERIFY TOKEN ==================

    // Access token: hợp lệ khi chữ ký đúng + còn hạn + KHÔNG nằm trong blacklist Redis
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
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    // Refresh token: hợp lệ khi chữ ký đúng + còn hạn + TỒN TẠI
    // Redis. Tách riêng lỗi "hết hạn" / "không tồn tại" để FE phân biệt được.
    private SignedJWT verifyRefreshToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        if (!signedJWT.verify(verifier)) {
            log.warn("Refresh token sai chu ky.");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (!signedJWT.getJWTClaimsSet().getExpirationTime().after(new Date())) {
            log.warn("Refresh token da het han. jwtId={}", signedJWT.getJWTClaimsSet().getJWTID());
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        if (!this.refreshTokenRepository.existsById(jwtId)) {
            // CHỈ log jwtId
            log.warn("Refresh token khong ton tai trong Redis (da dung / da thu hoi / khong hop le). jwtId={}",
                    jwtId);
            throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return signedJWT;
    }


    // Claim "scope" chứa các tên Role cách nhau bởi khoảng trắng (vd "ADMIN
    // USER")
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