package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 判断用户是否对活动进行收藏
 */
@TableName(value ="user_activity_favorite")
@Data
public class UserActivityFavorite {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer activityId;
}
