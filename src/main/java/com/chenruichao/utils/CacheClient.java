package com.chenruichao.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.chenruichao.utils.RedisConstants.MUTEX_LOCK_TTL;

/**
 * 抽象缓存工具
 * @author chenruichao
 */
@Component
public class CacheClient {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * 设置缓存
     * @author chenruichao
     * @param key 查询当前缓存的key
     * @param object 缓存的内容
     * @param expireTime 过期时间
     * @param timeUnit 过期时间单位
     */
    public void set(String key, Object object, Long expireTime, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object), expireTime, timeUnit);
    }

    /**
     * 逻辑过期策略
     * @param key 查询当前缓存的key
     * @param object 缓存的内容
     * @param expireTime 逻辑过期时间
     * @param timeUnit 时间单位
     */
    public void setWithLogicalExpiration(String key, Object object, Long expireTime, TimeUnit timeUnit) {
        // 封装缓存数据和逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询缓存 防止缓存穿透 缓存重构
     * @param keyPrefix 缓存key前缀
     * @param id 缓存数据的数据库主键
     * @param type 缓存对象的Class对象
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @param dbFallback 调用者查询的数据库的方法
     */
    public <R, T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Long expireTime,
                                         TimeUnit timeUnit, Function<T, R> dbFallback) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 缓存命中读取缓存即可
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 命中空对象缓存 直接返回空对象
        if ("".equals(json)) {
            return null;
        }

        // 缓存未命中尝试重构缓存 调用dbFallback.apply查询数据库获取数据
        R value = dbFallback.apply(id);

        // value为空说明当前数据不存在 设置空对象减数据库重复查询不存在的数据
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, "", expireTime, timeUnit);
            return null;
        }

        // 设置空对象缓存
        set(key, value, expireTime, timeUnit);

        return value;
    }

    /**
     * 查询缓存 使用逻辑过期防止缓存击穿
     * @param keyPrefix 缓存key前缀
     * @param lockKeyPrefix 锁的前缀
     * @param id 缓存数据的数据库主键
     * @param type 缓存对象的Class对象
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @param dbFallback 调用者查询的数据库的方法
     */
    public <R, T> R queryWithCacheBreakdown(String keyPrefix, String lockKeyPrefix, T id, Class<R> type, Long expireTime,
                                          TimeUnit timeUnit, Function<T, R> dbFallback) {
        String key = keyPrefix + id;
        // 利用setnx方法来创建一把简易分布式锁
        String lockKey = lockKeyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 命中空对象缓存 直接返回
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisdata = JSONUtil.toBean(json, RedisData.class);

        // 缓存对象中的数据为空 直接返回
        if (redisdata.getData() == null) {
            return null;
        }

        // 解析出缓存对象的数据
        R value = JSONUtil.toBean((JSONObject) redisdata.getData(), type);

        // 判断是否过期 有效时间未过缓存有效直接返回
        if (redisdata.getExpireTime() != null && redisdata.getExpireTime().isAfter(LocalDateTime.now())) {
            return value;
        }

        // 尝试获取锁
        // 获取锁成功: 提交任务 + 异步查询数据重构缓存
        // 换取锁失败: 已有线程正在重构缓存 直接返回旧数据即可
        boolean isLock = tryLock(lockKey);

        if (!isLock) {
            return value;
        }

        // 双重校验 获取锁之后判断当前缓存是否存在并有效
        json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {

            redisdata = JSONUtil.toBean(json, RedisData.class);

            if (redisdata != null && redisdata.getData() != null) {
                value = JSONUtil.toBean((JSONObject) redisdata.getData(), type);
            }

            if (redisdata != null && redisdata.getExpireTime() != null && redisdata.getExpireTime().isAfter(LocalDateTime.now())) {
                unlock(lockKey);
                return value;
            }
        }

        // 1.缓存未重构或已经失效 提交任务异步处理
        executorService.submit(() -> {
            try {
                // 尝试重构缓存 调用dbFallback.apply查询数据库获取数据
                R newValue = dbFallback.apply(id);

                // 数据不为空 设置逻辑过期
                if (newValue != null) {
                    setWithLogicalExpiration(key, newValue, expireTime, timeUnit);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放分布式锁
                unlock(lockKey);
            }
        });

        // 2. 直接返回旧数据即可
        return value;
    }

    /**
     * 利用Redis的setnx不存在才创建 来实现一把简单不可重入的分布式锁
     * @param key 锁名称
     */
    private boolean tryLock(String key) {
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(key, "Lock", MUTEX_LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(res);
    }

    /**
     * 释放分布式锁 删除对于的key 允许其他的请求创建锁
     * @param key 锁名称
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
