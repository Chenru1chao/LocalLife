package com.chenruichao.service;

import com.chenruichao.dto.Result;
import com.chenruichao.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    Result queryById(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryLikedUserList(Long id);

    Result saveBlog(Blog blog);

    Result queryByOfFollow(Long id, Integer offset);
}
