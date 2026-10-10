package com.chenruichao.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.chenruichao.dto.LoginFormDTO;
import com.chenruichao.dto.Result;
import com.chenruichao.dto.UserDTO;
import com.chenruichao.entity.User;
import com.chenruichao.entity.UserInfo;
import com.chenruichao.service.IUserInfoService;
import com.chenruichao.service.IUserService;
import com.chenruichao.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 验证码登录和密码登录共用这个接口 前端按传的字段区分
     * 带 code 走验证码登录 带 password 走密码登录
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return StrUtil.isNotBlank(loginForm.getCode())
                ? userService.loginByCode(loginForm)
                : userService.loginByPassword(loginForm);
    }

    /**
     * 设置或重置密码 需要短信验证码校验身份
     */
    @PostMapping("/password")
    public Result setPassword(@RequestBody LoginFormDTO loginForm) {
        return userService.setPassword(loginForm);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletRequest httpServletRequest) {
        return userService.logout(httpServletRequest);
    }

    @GetMapping("/me")
    public Result me() {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO != null) {
            return Result.ok(userDTO);
        }
        else {
            return Result.fail("信息获取异常");
        }
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }
}
