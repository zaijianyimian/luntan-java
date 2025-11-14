package com.example.likes.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
/**
 * 评论点赞表
 */
@TableName(value = "comment_like")
@Data
public class CommentLike {
    private Long commentId;
    private Long userId;
    private Integer activityId;
    private java.util.Date createdAt;
}
