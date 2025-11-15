package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message", "token"})
public class LoginSuccessDto extends BaseDto {
    private String token;

    public LoginSuccessDto(String token) {
        super(200, "登陆成功");
        this.token = token;
    }

    public LoginSuccessDto() {
        super(200, "登陆成功");
    }
}
