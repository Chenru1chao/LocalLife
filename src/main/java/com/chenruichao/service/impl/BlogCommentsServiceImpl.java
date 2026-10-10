package com.chenruichao.service.impl;

import com.chenruichao.entity.BlogComments;
import com.chenruichao.mapper.BlogCommentsMapper;
import com.chenruichao.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
