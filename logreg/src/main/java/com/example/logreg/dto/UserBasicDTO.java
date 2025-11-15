package com.example.logreg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBasicDTO {
    private Long userId;
    private String username;
    private String avatarObject;
    private String avatarUrl;
}