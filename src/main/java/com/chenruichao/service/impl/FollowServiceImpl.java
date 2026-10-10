package com.chenruichao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chenruichao.dto.Result;
import com.chenruichao.dto.UserDTO;
import com.chenruichao.entity.Follow;
import com.chenruichao.mapper.FollowMapper;
import com.chenruichao.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.service.IUserService;
import com.chenruichao.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;

import static com.chenruichao.utils.RedisConstants.COMMON_FOLLOW_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        if (checkLoginStatus()) {
            return Result.fail("登录后才可关注");
        }
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success = save(follow);
            if (success) {
                stringRedisTemplate.opsForSet().add(COMMON_FOLLOW_KEY + userId, id.toString());
            }
        } else {
            boolean success = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", id));
            if (success) {
                stringRedisTemplate.opsForSet().remove(COMMON_FOLLOW_KEY + userId, id.toString());
            }
        }
        return Result.ok();
    }

    private boolean checkLoginStatus() {
        return UserHolder.getUser() == null;
    }

    @Override
    public Result isFollow(Long id) {
        if (checkLoginStatus()) {
            return Result.fail("登录后才可查看是否关注");
        }
        Long userId = UserHolder.getUser().getId();
        Boolean res1 = stringRedisTemplate.opsForSet().isMember(COMMON_FOLLOW_KEY + userId, id.toString());
        if (BooleanUtil.isTrue(res1)) {
            return Result.ok(true);
        }

        Long res2 = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(res2 > 0);
    }

    @Override
    public Result followCommons(Long id) {
        if (checkLoginStatus()) {
            return Result.fail("登录后才可查看共同关注");
        }
        Long userId = UserHolder.getUser().getId();
        Set<String> idStrList = stringRedisTemplate.opsForSet().intersect(COMMON_FOLLOW_KEY + userId,
                COMMON_FOLLOW_KEY + id);
        if (idStrList == null || idStrList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> idLongList = idStrList.stream().map(Long::valueOf).toList();
        List<UserDTO> userDTOList = userService.listByIds(idLongList).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
        return Result.ok(userDTOList);
    }
}
