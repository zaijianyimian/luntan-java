package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 判断用户是否对活动进行点赞
 */
@TableName(value ="user_rel_likes")
@Data
public class UserRelLikes {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer activityId;
}
