package com.example.likes.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.likes.domain.CommentCountLike;

public interface CommentCountLikeService extends IService<CommentCountLike> {
    int upsertBatch(java.util.List<CommentCountLike> items);
}
