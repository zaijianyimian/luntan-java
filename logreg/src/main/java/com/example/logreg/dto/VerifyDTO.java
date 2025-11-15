package com.example.logreg.dto;

import lombok.Data;

@Data
public class VerifyDTO {
    private String email;
    private String code;
    private String username;
    private String password;
    private Double latitude;
    private Double longitude;
}
