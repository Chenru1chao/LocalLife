package com.chenruichao.config;

import com.chenruichao.intercept.LoginInterceptor;
import com.chenruichao.intercept.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    private final RefreshTokenInterceptor refreshTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
       registry.addInterceptor(loginInterceptor).excludePathPatterns(
               "/shop/**",
               "/voucher/**",
               "/shop-type/**",
               "/upload/**",
               "/blog/hot",
               "/user/code",
               "/user/login",
               "/user/password"
       ).order(1);
       registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);
    }
}
