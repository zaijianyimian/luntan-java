package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message"})
public class ChangeSuccessDto extends BaseDto {
    public ChangeSuccessDto(){
        super(200, "修改密码成功");
    }
}
