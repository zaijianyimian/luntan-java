package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * @TableName comments
 */
@TableName(value ="comments")
@Data
@Document(indexName = "comment-all")
public class Comments {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long userId;

    private Integer activityId;

    private String description;

    private Long countLikes;
}