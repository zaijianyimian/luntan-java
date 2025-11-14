package com.example.likes.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.likes.domain.CommentLike;

public interface CommentLikeService extends IService<CommentLike> {
    int insertIgnoreBatch(java.util.List<CommentLike> items);
}
