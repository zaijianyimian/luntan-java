package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message"})
public class LoginFailureDto extends BaseDto {
    public LoginFailureDto() {
        super(400, "请检查用户名或密码");
    }
}
