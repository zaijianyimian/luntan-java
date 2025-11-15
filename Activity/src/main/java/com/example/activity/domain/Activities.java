package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.example.activity.config.PointDeserializer;
import com.example.activity.pointhandler.GeoPoint;
import com.example.activity.vo.ActivityVO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.geo.Point;

@TableName(value = "activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class Activities implements Serializable {

    @Id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Long authorId;

    private String title;

    private String description;

    private Integer categoryId;

    private Integer countLikes;

    private Integer countComments;

    private Integer countViews;

    private String tags;

    private Date time;

    private Integer countFavorite;

    private Double latitude;

    private Double longitude;

    @JsonProperty("location")
    @JsonDeserialize(using = PointDeserializer.class)
    @TableField(exist = false)  // 关键：不参与 MyBatis-Plus 的 insert/update 映射
    private GeoPoint location;

    private Integer isTop;

    private Date topTime;

    private String authorImage;

    private Integer countPhotos;

    // Constructor to convert ActivityVO to Activities
    public Activities(ActivityVO activityVO) {
        this.title = activityVO.getTitle();
        this.description = activityVO.getDescription();
        this.categoryId = activityVO.getCategoryId();
        this.tags = activityVO.getTags();
        this.latitude = activityVO.getLatitude();
        this.longitude = activityVO.getLongitude();

        if (this.latitude != null && this.longitude != null) {
            this.location = new GeoPoint(this.longitude, this.latitude);  
            log.error("Location:"+location.toString());
        } else {
            this.location = new GeoPoint(0.0,0.0);
        }

        this.authorImage = activityVO.getAuthorImage();
        this.countPhotos = activityVO.getCountPhotos();
    }
}
