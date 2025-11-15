package com.example.logreg.dto.extend;

import com.example.logreg.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message"})
public class RegisterFailureDto extends BaseDto {
    public RegisterFailureDto(String message) {
        super(400, message);
    }
}
