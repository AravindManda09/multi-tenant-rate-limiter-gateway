package com.rate_limiter_gateway.filter;

import com.rate_limiter_gateway.config.TierLimitResolver;
import com.rate_limiter_gateway.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<List> rateLimitScript;

    @Mock
    private GatewayFilterChain chain;

    private RateLimiterFilter filter;
    private TierLimitResolver tierLimitResolver;

    @BeforeEach
    void setUp() {
        tierLimitResolver = new TierLimitResolver();
        filter = new RateLimiterFilter(redisTemplate, rateLimitScript, tierLimitResolver);
    }

    private MockServerWebExchange exchangeWithTenantContext(String tenantId, String tier) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything").build()
        );
        exchange.getAttributes().put(
                TenantContextFilter.TENANT_CONTEXT_KEY,
                new TenantContext(tenantId, tier)
        );
        return exchange;
    }

    @Test
    void noTenantContext_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything").build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void redisAllows_returns200WithRemainingHeader() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 42L)));

        MockServerWebExchange exchange = exchangeWithTenantContext("tenant-123", "FREE");

        filter.filter(exchange, chain).block();

        assertEquals("42", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        verify(chain).filter(exchange);
    }

    @Test
    void redisRejects_returns429WithRetryAfterAndBody() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L)));

        MockServerWebExchange exchange = exchangeWithTenantContext("tenant-123", "FREE");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        verify(chain, never()).filter(any());
    }

    @Test
    void redisDown_failsOpenAndContinues() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Connection refused")));

        MockServerWebExchange exchange = exchangeWithTenantContext("tenant-456", "PRO");

        filter.filter(exchange, chain).block();

        // Should fail open — request continues, not rejected
        assertEquals("unavailable", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Status"));
        verify(chain).filter(exchange);
    }

    @Test
    void redisTimeout_failsOpenAndContinues() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new java.util.concurrent.TimeoutException("Redis timed out")));

        MockServerWebExchange exchange = exchangeWithTenantContext("tenant-789", "ENTERPRISE");

        filter.filter(exchange, chain).block();

        assertEquals("unavailable", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Status"));
        verify(chain).filter(exchange);
    }

    @Test
    void filterOrder_isHighestPrecedencePlusOne() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, filter.getOrder());
    }
}