package com.example.laptopshop.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

public class PasswordValidator implements ConstraintValidator<PasswordConstraint, String> {

    private int min;

    @Override
    public void initialize(PasswordConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        // Lấy giá trị min được truyền vào từ Annotation
        this.min = constraintAnnotation.min();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Nếu password null, ta trả về true để việc kiểm tra null cho @NotBlank
        if (Objects.isNull(value)) {
            return true;
        }
        // Kiểm tra độ dài password
        return value.length() >= min;
    }
}