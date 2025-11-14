package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 统计活动点赞总数
 */
@TableName(value ="activity_count_like")
@Data
public class ActivityCountLike {
    @TableId(value = "activity_id", type = IdType.INPUT)
    private Integer activityId;

    private Long countLikes;
}
