package com.rate_limiter_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class TierLimitResolver {

    private long windowMs = 60000;
    private Map<String, Long> tiers = Map.of(
            "FREE", 60L,
            "PRO", 500L,
            "ENTERPRISE", 5000L
    );

    public long getWindowMs() {
        return windowMs;
    }

    public void setWindowMs(long windowMs) {
        this.windowMs = windowMs;
    }

    public Map<String, Long> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, Long> tiers) {
        this.tiers = tiers;
    }

    public long resolveLimit(String tier) {
        if (tier == null) {
            return tiers.getOrDefault("FREE", 60L);
        }
        return tiers.getOrDefault(tier.toUpperCase(), tiers.getOrDefault("FREE", 60L));
    }
}