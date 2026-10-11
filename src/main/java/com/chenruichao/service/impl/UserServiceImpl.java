package com.chenruichao.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.dto.LoginFormDTO;
import com.chenruichao.dto.Result;
import com.chenruichao.dto.UserDTO;
import com.chenruichao.entity.User;
import com.chenruichao.mapper.UserMapper;
import com.chenruichao.service.IUserService;
import com.chenruichao.utils.PasswordEncoder;
import com.chenruichao.utils.RedisConstants;
import com.chenruichao.utils.RegexUtils;
import com.chenruichao.utils.SystemConstants;
import com.chenruichao.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chenruichao.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("手机号:{}的验证码发送成功 验证码为{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result loginByCode(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String codeKey = LOGIN_CODE_KEY + loginForm.getPhone();
        String code = stringRedisTemplate.opsForValue().get(codeKey);

        if (code == null) {
            return Result.fail("验证码已过期 请重新获取");
        }

        if (!code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误 请重新输入验证码");
        }

        User user = query().eq("phone", phone).one();

        // 没查询到说明当前用户是第一次登录 直接帮用户注册
        if (user == null) {
            user = createUserWithPhone(phone);
            save(user);
        }

        // 验证码是一次性的 用完就删掉
        stringRedisTemplate.delete(codeKey);

        return Result.ok(saveLoginUser(user));
    }

    @Override
    public Result loginByPassword(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        User user = query().eq("phone", phone).one();

        if (user == null) {
            return Result.fail("手机号未注册");
        }

        // 库里的密码是空串 说明这个账号从没设置过密码 引导用户走验证码登录
        if (StrUtil.isBlank(user.getPassword())) {
            return Result.fail("该账号未设置密码 请使用验证码登录");
        }

        if (!PasswordEncoder.matches(user.getPassword(), loginForm.getPassword())) {
            return Result.fail("密码错误 请重新输入");
        }

        return Result.ok(saveLoginUser(user));
    }

    @Override
    public Result setPassword(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String codeKey = LOGIN_CODE_KEY + phone;
        String code = stringRedisTemplate.opsForValue().get(codeKey);

        if (code == null) {
            return Result.fail("验证码已过期 请重新获取");
        }

        if (!code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误 请重新输入验证码");
        }

        String password = loginForm.getPassword();

        if (StrUtil.isBlank(password)) {
            return Result.fail("密码不能为空");
        }

        User user = query().eq("phone", phone).one();

        // 没查到说明这个手机号还没注册过 顺手帮他注册
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        user.setPassword(PasswordEncoder.encode(password));
        saveOrUpdate(user);

        // 验证码是一次性的 用完即删
        stringRedisTemplate.delete(codeKey);

        return Result.ok();
    }

    /**
     * 把用户信息写进redis作为登录凭证 返回用户id当作token
     */
    private Long saveLoginUser(User user) {
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        Map<String, String> userDTOMap = new HashMap<>();
        userDTOMap.put("id", userDTO.getId().toString());
        userDTOMap.put("nickName", userDTO.getNickName());
        userDTOMap.put("icon", userDTO.getIcon());

        // 用户的登录凭证 前缀为login:token
        String tokenKey = RedisConstants.LOGIN_USER_KEY + user.getId();

        Boolean isExists = stringRedisTemplate.hasKey(tokenKey);

        if (!isExists) {
            stringRedisTemplate.opsForHash().putAll(tokenKey, userDTOMap);
        }

        return user.getId();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());

        return user;
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + ":" + keySuffix;
        int day = now.getDayOfMonth();

        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + ":" + keySuffix;
        int day = now.getDayOfMonth();

        List<Long> res = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.signed(day)).valueAt(0)
        );

        if (res == null || res.isEmpty()) {
            return Result.ok(0);
        }

        Long bits = res.get(0);
        if (bits == null || bits == 0) {
            return Result.ok(0);
        }

        int nums = 0;
        while ((bits & 1) != 0) {
           nums++;
           bits >>>= 1;
        }

        return Result.ok(nums);
    }

    @Override
    public Result logout(HttpServletRequest httpServletRequest) {
        String authorization = httpServletRequest.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY + authorization);
        return Result.ok();
    }
}
