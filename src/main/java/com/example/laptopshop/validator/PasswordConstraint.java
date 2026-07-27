package com.example.laptopshop.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD }) // Chỉ áp dụng cho các field (thuộc tính)
@Retention(RetentionPolicy.RUNTIME) // Tồn tại trong lúc runtime để Spring quét
@Constraint(validatedBy = { PasswordValidator.class }) // Chỉ định class xử lý logic validation
public @interface PasswordConstraint {

    // Message mặc định nếu không cấu hình, thường trả về key để map với ErrorCode
    String message() default "INVALID_PASSWORD";

    // Thuộc tính min tự định nghĩa (mặc định là 6)
    int min() default 6;

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}