package com.example.logreg.dto;

import lombok.Data;

@Data
public class ChangePassVO {
    private String oldPassword;
    private String newPassword;
}