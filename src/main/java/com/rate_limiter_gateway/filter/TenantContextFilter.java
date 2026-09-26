package com.rate_limiter_gateway.filter;

import com.rate_limiter_gateway.security.JwtService;
import com.rate_limiter_gateway.security.TenantContext;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TenantContextFilter implements GlobalFilter, Ordered {

    public static final String TENANT_CONTEXT_KEY =
            "tenantContext";

    private static final Logger log =
            LoggerFactory.getLogger(TenantContextFilter.class);

    private final JwtService jwtService;

    public TenantContextFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain
    ) {

        String authorizationHeader =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }

        String token =
                authorizationHeader.substring(7);

        try {

            Claims claims =
                    jwtService.validateToken(token);

            String tenantId =
                    claims.get("tenant_id", String.class);

            String tier =
                    claims.get("tier", String.class);

            if (tenantId == null || tier == null) {

                exchange.getResponse()
                        .setStatusCode(HttpStatus.UNAUTHORIZED);

                return exchange.getResponse().setComplete();
            }

            if (!tier.equals("FREE")
                    && !tier.equals("PRO")
                    && !tier.equals("ENTERPRISE")) {

                exchange.getResponse()
                        .setStatusCode(HttpStatus.UNAUTHORIZED);

                return exchange.getResponse().setComplete();
            }

            TenantContext tenantContext =
                    new TenantContext(tenantId, tier);

            exchange.getAttributes()
                    .put(TENANT_CONTEXT_KEY, tenantContext);

            // Temporary log for testing
            log.info(
                    "TenantContext created: tenantId={}, tier={}",
                    tenantContext.tenantId(),
                    tenantContext.tier()
            );

            return chain.filter(exchange);

        } catch (Exception exception) {

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}