package com.example.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.activity.domain.ActivityCategories;
import com.example.activity.service.ActivityTagsService;
import com.example.activity.mapper.ActivityTagsMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【activity_tags】的数据库操作Service实现
* @createDate 2025-11-08 13:55:52
*/
@Service
public class ActivityTagsServiceImpl extends ServiceImpl<ActivityTagsMapper, ActivityCategories>
    implements ActivityTagsService{

}




