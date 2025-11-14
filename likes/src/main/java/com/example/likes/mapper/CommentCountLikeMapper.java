package com.example.likes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.likes.domain.CommentCountLike;

public interface CommentCountLikeMapper extends BaseMapper<CommentCountLike> {
    int upsertBatch(java.util.List<CommentCountLike> items);
}
