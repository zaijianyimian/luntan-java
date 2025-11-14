package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 *判断评论点赞总数
 */
@TableName(value ="comments")
@Data
public class Comments {
    private Long id;

    private Long userId;

    private Integer activityId;

    private String description;

    private Long countLikes;
}