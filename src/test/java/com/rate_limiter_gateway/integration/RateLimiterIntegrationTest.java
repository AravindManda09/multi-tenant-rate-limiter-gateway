package com.rate_limiter_gateway.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RateLimiterIntegrationTest {

    // Spins up a REAL Redis container — not a mock
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379);

    // Tell Spring Boot to connect to the Testcontainers Redis instead of localhost
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<List> rateLimitScript;

    // Flush Redis before each test so counters don't leak between tests
    @BeforeEach
    void cleanRedis() {
        redisTemplate.execute(connection ->
                connection.serverCommands().flushAll()
        ).blockLast();
    }

    private List executeScript(String tenantId, long windowMs, long limit) {
        List<String> keys = List.of("ratelimit:" + tenantId);
        List<String> args = List.of(
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMs),
                String.valueOf(limit)
        );
        return redisTemplate.execute(rateLimitScript, keys, args)
                .next()
                .block();
    }

    @Test
    void requestsWithinLimit_areAllAllowed() {
        long limit = 5;

        for (int i = 1; i <= 5; i++) {
            List result = executeScript("tenant-test", 60000, limit);
            long allowed = ((Number) result.get(0)).longValue();
            assertEquals(1L, allowed, "Request " + i + " should be allowed");
        }
    }

    @Test
    void requestExceedingLimit_isRejected() {
        long limit = 5;

        // Use up the entire quota
        for (int i = 0; i < 5; i++) {
            executeScript("tenant-test", 60000, limit);
        }

        // 6th request should be rejected
        List result = executeScript("tenant-test", 60000, limit);
        long allowed = ((Number) result.get(0)).longValue();
        assertEquals(0L, allowed, "Request 6 should be rejected");
    }

    @Test
    void remainingCounter_decrementsCorrectly() {
        long limit = 5;

        for (int i = 0; i < 5; i++) {
            List result = executeScript("tenant-test", 60000, limit);
            long remaining = ((Number) result.get(1)).longValue();
            assertEquals(limit - i - 1, remaining, "Remaining should be " + (limit - i - 1));
        }
    }

    @Test
    void differentTenants_haveIndependentQuotas() {
        long limit = 3;

        // Exhaust tenant-A's quota
        for (int i = 0; i < 3; i++) {
            executeScript("tenant-A", 60000, limit);
        }

        // Tenant-A should be rejected
        List resultA = executeScript("tenant-A", 60000, limit);
        assertEquals(0L, ((Number) resultA.get(0)).longValue(), "Tenant A should be rejected");

        // Tenant-B should still be allowed — completely independent
        List resultB = executeScript("tenant-B", 60000, limit);
        assertEquals(1L, ((Number) resultB.get(0)).longValue(), "Tenant B should still be allowed");
    }

    @Test
    void expiredWindow_resetsQuota() throws InterruptedException {
        long limit = 3;
        long windowMs = 1000; // 1 second window for fast testing

        // Exhaust the quota
        for (int i = 0; i < 3; i++) {
            executeScript("tenant-test", windowMs, limit);
        }

        // Should be rejected
        List rejected = executeScript("tenant-test", windowMs, limit);
        assertEquals(0L, ((Number) rejected.get(0)).longValue(), "Should be rejected");

        // Wait for the window to expire
        Thread.sleep(1500);

        // Should be allowed again — quota resets after the window
        List allowed = executeScript("tenant-test", windowMs, limit);
        assertEquals(1L, ((Number) allowed.get(0)).longValue(), "Should be allowed after window expires");
    }
}