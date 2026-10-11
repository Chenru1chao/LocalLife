package com.chenruichao.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // TODO: 这个地方硬编码
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("20060213");
        return Redisson.create(config);
    }
}
