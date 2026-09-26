package com.rate_limiter_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RateLimitScriptConfig {

    @Bean
    public RedisScript<List> rateLimiterRedisScript() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/sliding-window-rate-limiter.lua"));
        redisScript.setResultType(List.class);
        return redisScript;
    }
}