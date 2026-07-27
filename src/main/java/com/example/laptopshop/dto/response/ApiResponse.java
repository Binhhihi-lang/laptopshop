package com.example.laptopshop.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL) //  cái nào rỗng thì không hiện ra
@Getter
@Setter
public class ApiResponse<T> {

    private int code = 1000; // Mặc định là lỗi, code = "1000" là thành công
    private String message;
    private T result;

}
