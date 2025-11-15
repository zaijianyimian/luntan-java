package com.example.logreg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NearbyUserDTO {
    private Long userId;
    private String username;
    private String nickname;
    private Double distanceKm;
}