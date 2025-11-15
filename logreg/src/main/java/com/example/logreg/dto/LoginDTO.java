package com.example.logreg.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String username;
    private String password;
    private Double latitude;
    private Double longitude;
}