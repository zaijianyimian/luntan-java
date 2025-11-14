package com.example.likes.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.likes.domain.CommentCountLike;
import com.example.likes.mapper.CommentCountLikeMapper;
import com.example.likes.service.CommentCountLikeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentCountLikeServiceImpl extends ServiceImpl<CommentCountLikeMapper, CommentCountLike>
        implements CommentCountLikeService {

    @Override
    public int upsertBatch(List<CommentCountLike> items) {
        if (items == null || items.isEmpty()) return 0;
        return this.baseMapper.upsertBatch(items);
    }
}
