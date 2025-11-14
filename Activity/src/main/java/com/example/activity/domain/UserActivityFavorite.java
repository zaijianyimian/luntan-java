package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName user_activity_favorite
 */
@TableName(value ="user_activity_favorite")
@Data
public class UserActivityFavorite {
    private Long userId;

    private Integer activityId;
}