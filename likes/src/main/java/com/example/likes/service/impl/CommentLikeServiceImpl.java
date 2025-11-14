package com.example.likes.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.likes.domain.CommentLike;
import com.example.likes.mapper.CommentLikeMapper;
import com.example.likes.service.CommentLikeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentLikeServiceImpl extends ServiceImpl<CommentLikeMapper, CommentLike>
        implements CommentLikeService {

    @Override
    public int insertIgnoreBatch(List<CommentLike> items) {
        if (items == null || items.isEmpty()) return 0;
        return this.baseMapper.insertIgnoreBatch(items);
    }
}
