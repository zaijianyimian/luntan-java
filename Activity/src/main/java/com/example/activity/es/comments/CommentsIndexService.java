package com.example.activity.es.comments;

import com.example.activity.pojo.CommentESSave;

import java.util.List;
import java.util.Optional;

public interface CommentsIndexService {

    void indexAfterCommit(CommentESSave comment);
    void deleteAfterCommit(Integer id);
    List<CommentESSave> findByActivityId(Integer activityId);
    // 新增：按评论ID查询（用于点赞/取消点赞时缓存缺失回填）
    CommentESSave findById(Integer id);
}
