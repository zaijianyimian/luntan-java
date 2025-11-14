package com.example.likes.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.likes.domain.Comments;
import com.example.likes.service.CommentsService;
import com.example.likes.mapper.CommentsMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【comments】的数据库操作Service实现
* @createDate 2025-11-14 21:53:55
*/
@Service
public class CommentsServiceImpl extends ServiceImpl<CommentsMapper, Comments>
    implements CommentsService{

}




