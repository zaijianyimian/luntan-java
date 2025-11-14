package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class LoginFailureDto extends BaseDto {

    public LoginFailureDto() {
        super(400, "请检查用户名或密码或邮箱");
    }
}
