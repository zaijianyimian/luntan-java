package com.example.likes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.likes.domain.CommentLike;

public interface CommentLikeMapper extends BaseMapper<CommentLike> {
    int insertIgnoreBatch(java.util.List<CommentLike> items);
}
