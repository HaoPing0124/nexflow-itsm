package com.nexflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器独立配置。
 *
 * 不能把这个 Bean 放在 {@link SecurityConfig} 里：SecurityConfig 依赖 JwtFilter，
 * JwtFilter 依赖 UserService，UserService 依赖 PasswordEncoder，
 * 三者会构成 jwtFilter → userService → securityConfig → jwtFilter 的循环依赖，
 * 导致应用启动即失败（Spring Boot 默认禁止循环引用）。
 * 把 PasswordEncoder 抽出来，依赖图即变为无环：
 * securityConfig → jwtFilter → userService → passwordEncoder。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
