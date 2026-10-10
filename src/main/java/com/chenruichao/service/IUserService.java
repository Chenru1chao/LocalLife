package com.chenruichao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chenruichao.dto.LoginFormDTO;
import com.chenruichao.dto.Result;
import com.chenruichao.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result loginByCode(LoginFormDTO loginForm);

    Result loginByPassword(LoginFormDTO loginForm);

    Result setPassword(LoginFormDTO loginForm);

    Result sign();

    Result signCount();

    Result logout(HttpServletRequest httpServletRequest);
}
