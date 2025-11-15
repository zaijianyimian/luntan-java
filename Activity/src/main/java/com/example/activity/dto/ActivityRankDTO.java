package com.example.activity.dto;

import lombok.Data;

@Data
public class ActivityRankDTO {
    private Integer activityId;
    private Double score;
    private String title;
}