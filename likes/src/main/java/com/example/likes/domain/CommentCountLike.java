package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/**
 * 评论点赞数
 */
@TableName(value = "comment_count_like")
@Data
public class CommentCountLike {
    @TableId(value = "comment_id", type = IdType.INPUT)
    private Long commentId;

    private Integer activityId;

    private Long countLikes;

    private java.sql.Timestamp updatedAt;
}
