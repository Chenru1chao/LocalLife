package com.chenruichao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.chenruichao.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class LocalLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalLifeApplication.class, args);
    }

}
