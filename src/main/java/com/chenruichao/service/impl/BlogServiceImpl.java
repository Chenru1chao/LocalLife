package com.chenruichao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.dto.Result;
import com.chenruichao.dto.ScrollResult;
import com.chenruichao.dto.UserDTO;
import com.chenruichao.entity.Blog;
import com.chenruichao.entity.Follow;
import com.chenruichao.entity.User;
import com.chenruichao.mapper.BlogMapper;
import com.chenruichao.service.IBlogService;
import com.chenruichao.service.IFollowService;
import com.chenruichao.service.IUserService;
import com.chenruichao.utils.SystemConstants;
import com.chenruichao.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.chenruichao.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.chenruichao.utils.RedisConstants.FEED_KEY;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query().orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();

        records.forEach(blog -> {
            getUserMsg(blog);
            updateLikeStatus(blog);
        });
        return Result.ok(records);
    }

    private void getUserMsg(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    public void updateLikeStatus(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        Double res = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(res != null);
    }

    @Override
    @Transactional
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double res = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (res != null) {
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), (double) System.currentTimeMillis());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryLikedUserList(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> res = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (res == null || res.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIdList = res.stream().map(Long::valueOf).toList();
        String idStr = StrUtil.join(",", userIdList);
        List<UserDTO> userDTOList = userService.query().in("id", userIdList).last("order by field(id," + idStr + ")")
                .list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        boolean success = save(blog);
        if (!success) {
            return Result.fail("新增笔记失败");
        }
        List<Follow> followUserId = followService.query().eq("follow_user_id", userId).list();
        for (Follow follow : followUserId) {
            stringRedisTemplate.opsForZSet().add(FEED_KEY + follow.getUserId(),
                    blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        getUserMsg(blog);
        updateLikeStatus(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryByOfFollow(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, Long.valueOf(offset), 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult scrollResult = new ScrollResult();
            scrollResult.setList(Collections.emptyList());
            scrollResult.setOffset(offset);
            scrollResult.setMinTime(lastId);
            return Result.ok(scrollResult);
        }
        int nums = 1;
        long minScore = 0;
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String curUserId = typedTuple.getValue();
            ids.add(Long.valueOf(curUserId));
            long score = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (score == minScore) {
                nums++;
            } else {
                minScore = score;
                nums = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list()
                .stream().map(blog -> {
                    getUserMsg(blog);
                    updateLikeStatus(blog);
                    return blog;
                }).toList();
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(lastId == minScore ? nums + offset : nums);
        scrollResult.setMinTime(minScore);
        return Result.ok(scrollResult);
    }
}
