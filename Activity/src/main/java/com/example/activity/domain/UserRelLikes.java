package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName user_rel_likes
 */
@TableName(value ="user_rel_likes")
@Data
public class UserRelLikes {
    private Long userId;

    private Integer activityId;
}