package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import lombok.Data;

@Data
public class TokenTest extends BaseDto {
    public TokenTest(Integer code, String message) {
        super(code, message);
    }
}
