package com.example.logreg.dto;

import lombok.Data;

@Data
public class RegisterVO {
    private String username;
    private String password;
    private String email;
}