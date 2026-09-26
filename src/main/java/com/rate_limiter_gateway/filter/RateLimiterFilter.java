package com.rate_limiter_gateway.filter;

import com.rate_limiter_gateway.config.TierLimitResolver;
import com.rate_limiter_gateway.security.TenantContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;
    private final TierLimitResolver tierLimitResolver;

    public RateLimiterFilter(
            ReactiveStringRedisTemplate redisTemplate,
            RedisScript<List> rateLimitScript,
            TierLimitResolver tierLimitResolver
    ) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.tierLimitResolver = tierLimitResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        TenantContext tenantContext = exchange.getAttribute(TenantContextFilter.TENANT_CONTEXT_KEY);

        if (tenantContext == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String tenantId = tenantContext.tenantId();
        String tier = tenantContext.tier();

        String redisKey = "ratelimit:" + tenantId;
        long nowMillis = System.currentTimeMillis();
        long windowMs = tierLimitResolver.getWindowMs();
        long limit = tierLimitResolver.resolveLimit(tier);

        List<String> keys = List.of(redisKey);
        List<String> args = List.of(
                String.valueOf(nowMillis),
                String.valueOf(windowMs),
                String.valueOf(limit)
        );

        return redisTemplate.execute(rateLimitScript, keys, args)
                .next()
                .flatMap(result -> {
                    // Result list: [allowed (0 or 1), remaining]
                    long allowed = ((Number) result.get(0)).longValue();
                    long remaining = ((Number) result.get(1)).longValue();

                    if (allowed == 1) {
                        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));
                        return chain.filter(exchange);
                    } else {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(windowMs / 1000));

                        byte[] bytes = "{\"error\": \"Quota Exceeded\"}".getBytes(StandardCharsets.UTF_8);
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}