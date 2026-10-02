package com.rate_limiter_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TierLimitResolverTest {

    private TierLimitResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TierLimitResolver();
    }

    @Test
    void freeTier_returns60() {
        assertEquals(60L, resolver.resolveLimit("FREE"));
    }

    @Test
    void proTier_returns500() {
        assertEquals(500L, resolver.resolveLimit("PRO"));
    }

    @Test
    void enterpriseTier_returns5000() {
        assertEquals(5000L, resolver.resolveLimit("ENTERPRISE"));
    }

    @Test
    void lowercaseTier_stillResolves() {
        assertEquals(500L, resolver.resolveLimit("pro"));
    }

    @Test
    void mixedCaseTier_stillResolves() {
        assertEquals(5000L, resolver.resolveLimit("Enterprise"));
    }

    @Test
    void unknownTier_defaultsToFree() {
        assertEquals(60L, resolver.resolveLimit("DIAMOND"));
    }

    @Test
    void nullTier_defaultsToFree() {
        assertEquals(60L, resolver.resolveLimit(null));
    }

    @Test
    void defaultWindowMs_is60000() {
        assertEquals(60000L, resolver.getWindowMs());
    }
}