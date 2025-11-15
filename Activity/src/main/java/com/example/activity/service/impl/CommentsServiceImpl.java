package com.example.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.activity.domain.Comments;
import com.example.activity.service.CommentsService;
import com.example.activity.mapper.CommentsMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【comments】的数据库操作Service实现
* @createDate 2025-11-08 13:55:58
*/
@Service
@org.springframework.transaction.annotation.Transactional
public class CommentsServiceImpl extends ServiceImpl<CommentsMapper, Comments>
    implements CommentsService{

}




