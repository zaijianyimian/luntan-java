package com.example.logreg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvatarUploadDTO {
    private boolean ok;
    private String url;
    private String error;
}