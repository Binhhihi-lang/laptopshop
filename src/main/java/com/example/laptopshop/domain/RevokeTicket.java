package com.example.laptopshop.domain;

import java.io.Serializable;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Vé tạm cấp khi login bị chặn vì vượt giới hạn thiết bị (lỗi 1013).
 *
 * <p>Vấn đề: lúc user bấm "Đăng xuất thiết bị cũ nhất" thì CHƯA có token (login
 * chưa thành công), nên cần cách chứng minh danh tính cho endpoint đó. Gửi lại
 * mật khẩu thì được nhưng password phải đi qua mạng lần 2. Thay vào đó, BE đã
 * verify mật khẩu ĐÚNG rồi mới trả 1013 -> chỉ cần "ghi nhớ" điều đó bằng vé
 * này.
 *
 * <p>Vé dùng MỘT LẦN (xóa ngay khi dùng) nên không replay được; TTL ngắn nên
 * không có giá trị lâu dài. Xem {@code PasswordResetToken} cho pattern tương tự.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@RedisHash(value = "REVOKE_TICKET")
public class RevokeTicket implements Serializable {

    @Id
    String id; // UUID — chính là vé gửi cho FE

    String userId;

    String deviceId; // thiết bị ĐANG xin đăng nhập (để cấp token sau khi đá máy cũ)

    @TimeToLive // đơn vị giây
    Long ttl;
}
