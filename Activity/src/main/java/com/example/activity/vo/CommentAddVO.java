package com.example.activity.vo;

import lombok.Data;

@Data
public class CommentAddVO {
    private Long userId;
    private Integer activityId;
    private String description;
}
