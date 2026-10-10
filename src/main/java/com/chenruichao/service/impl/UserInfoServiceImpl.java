package com.chenruichao.service.impl;

import com.chenruichao.entity.UserInfo;
import com.chenruichao.mapper.UserInfoMapper;
import com.chenruichao.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
