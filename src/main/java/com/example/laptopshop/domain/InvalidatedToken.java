package com.example.laptopshop.domain;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RedisHash(value = "INVALIDATED_TOKEN")
public class InvalidatedToken implements Serializable {

    // phải có Seriazalble để truyển object vào Redis
    @Id
    String id; // = jwtId (claim "jti") của token bị logout

    @TimeToLive // đơn vị mặc định: giây. Redis tự xóa key này khi hết TTL
    Long ttl;
}