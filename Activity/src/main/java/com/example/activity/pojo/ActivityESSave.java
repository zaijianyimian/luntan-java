package com.example.activity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.example.activity.domain.Activities;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.geo.Point;

import java.util.Date;

@Data
@NoArgsConstructor

@Document(indexName = "activity-all")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityESSave {
    @Id
    private Integer id;

    private Long authorId;

    private String title;

    private String description;

    private Integer categoryId;

    private String tags;

    private String authorImage;

    private Integer countPhotos;
    public ActivityESSave(Activities  activities){
        this.id = activities.getId();
        this.authorId = activities.getAuthorId();
        this.title = activities.getTitle();
        this.description = activities.getDescription();
        this.categoryId = activities.getCategoryId();
        this.tags = activities.getTags();
        this.authorImage = activities.getAuthorImage();
        this.countPhotos = activities.getCountPhotos();
    }
}
