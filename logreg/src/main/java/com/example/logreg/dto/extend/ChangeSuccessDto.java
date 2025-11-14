package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class ChangeSuccessDto extends BaseDto {

    public ChangeSuccessDto(){
        super(200, "修改密码成功");
    }

}
