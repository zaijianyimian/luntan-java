package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message"})
public class RegisterSuccessDto extends BaseDto {
    public RegisterSuccessDto() {
        super(200, "注册成功");
    }
}
