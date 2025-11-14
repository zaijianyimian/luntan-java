package com.example.activity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ActivityVO {
    private Long authorId;

    private String title;

    private String description;

    private Integer categoryId;

    private String tags;

    private Double latitude;

    private Double longitude;

    private String authorImage;

    private Integer countPhotos;
}
