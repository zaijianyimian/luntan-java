package com.example.likes.mapper;

import com.example.likes.domain.UserRelLikes;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author lenovo
* @description 针对表【user_rel_likes】的数据库操作Mapper
* @createDate 2025-11-14 19:33:15
* @Entity .domain.UserRelLikes
*/
public interface UserRelLikesMapper extends BaseMapper<UserRelLikes> {
    int insertIgnoreBatch(java.util.List<UserRelLikes> items);
    int deleteBatch(java.util.List<UserRelLikes> items);
}




