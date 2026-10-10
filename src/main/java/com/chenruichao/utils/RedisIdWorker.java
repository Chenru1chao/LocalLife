package com.chenruichao.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.chenruichao.utils.RedisConstants.NEXT_ID_KEY;


/**
 * 全局ID生成器
 * @author chenruichao
 * 0  TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT  OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO
 * ↑  ↑ 31 位时间差                    ↑ 32 位订单号
 * 符号位固定 0
 */
@Component
public class RedisIdWorker {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1767225600L;
    private static final long BITS_LENGTH = 32;

    public long nextId(String key) {
        long nowSecond = Instant.now().getEpochSecond();

        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        return (nowSecond - BEGIN_TIMESTAMP) << BITS_LENGTH
                | (stringRedisTemplate.opsForValue().increment(NEXT_ID_KEY + key + ":" + date));
    }

}
