package com.chenruichao.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 与 spring.redis.* 共用同一份配置 避免地址或密码写死在这里
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
