package com.chenruichao.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封装逻辑时间和缓存数据
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
