package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class LoginSuccessDto extends BaseDto {
    private String token;

    public LoginSuccessDto(String token) {
        super(200, "登陆成功");
        this.token = token;
    }

    // 添加无参构造函数以满足Lombok要求
    public LoginSuccessDto() {
        super(200, "登陆成功");
    }
}
