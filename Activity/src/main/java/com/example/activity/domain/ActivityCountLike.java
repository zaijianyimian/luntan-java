package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName activity_count_like
 */
@TableName(value ="activity_count_like")
@Data
public class ActivityCountLike {
    private Integer activityId;

    private Long countLikes;
}