package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class RegisterFailureDto extends BaseDto {
    public RegisterFailureDto(String message) {
        super(400, message);
    }
}
