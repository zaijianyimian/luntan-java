package com.example.likes.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.likes.domain.UserRelLikes;
import com.example.likes.service.UserRelLikesService;
import com.example.likes.mapper.UserRelLikesMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【user_rel_likes】的数据库操作Service实现
* @createDate 2025-11-14 19:33:15
*/
@Service
public class UserRelLikesServiceImpl extends ServiceImpl<UserRelLikesMapper, UserRelLikes>
    implements UserRelLikesService{
    @Override
    public int insertIgnoreBatch(java.util.List<UserRelLikes> items) {
        if (items == null || items.isEmpty()) return 0;
        return this.baseMapper.insertIgnoreBatch(items);
    }

    @Override
    public int deleteBatch(java.util.List<UserRelLikes> items) {
        if (items == null || items.isEmpty()) return 0;
        return this.baseMapper.deleteBatch(items);
    }
}




