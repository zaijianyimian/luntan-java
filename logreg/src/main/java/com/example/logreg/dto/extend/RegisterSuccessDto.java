package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class RegisterSuccessDto extends BaseDto {
    public RegisterSuccessDto() {
        super(200, "注册成功");
    }
}
