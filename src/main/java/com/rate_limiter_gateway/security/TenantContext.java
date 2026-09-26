package com.rate_limiter_gateway.security;

public record TenantContext(
            String tenantId,
            String tier
) {



  }
