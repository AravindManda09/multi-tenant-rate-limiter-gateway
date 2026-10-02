package com.rate_limiter_gateway.filter;

import com.rate_limiter_gateway.security.JwtService;
import com.rate_limiter_gateway.security.TenantContext;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private GatewayFilterChain chain;

    private TenantContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter(jwtService);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // Helper to build a mock Claims object
    private Claims buildClaims(String tenantId, String tier) {
        Claims claims = mock(Claims.class);
        when(claims.get("tenant_id", String.class)).thenReturn(tenantId);
        when(claims.get("tier", String.class)).thenReturn(tier);
        return claims;
    }

    @Test
    void noAuthHeader_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything").build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void authHeaderWithoutBearer_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Basic abc123")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void validToken_storesTenantContextAndContinues() {
        Claims claims = buildClaims("tenant-123", "FREE");
        when(jwtService.validateToken("valid-token")).thenReturn(claims);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer valid-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        // Verify the filter stored TenantContext in the exchange
        TenantContext ctx = exchange.getAttribute(TenantContextFilter.TENANT_CONTEXT_KEY);
        assertNotNull(ctx);
        assertEquals("tenant-123", ctx.tenantId());
        assertEquals("FREE", ctx.tier());

        // Verify the request continued down the chain
        verify(chain).filter(exchange);
    }

    @Test
    void validTokenWithProTier_storesTenantContext() {
        Claims claims = buildClaims("tenant-456", "PRO");
        when(jwtService.validateToken("pro-token")).thenReturn(claims);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer pro-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        TenantContext ctx = exchange.getAttribute(TenantContextFilter.TENANT_CONTEXT_KEY);
        assertNotNull(ctx);
        assertEquals("PRO", ctx.tier());
        verify(chain).filter(exchange);
    }

    @Test
    void missingTenantId_returns401() {
        Claims claims = buildClaims(null, "FREE");
        when(jwtService.validateToken("no-tenant-token")).thenReturn(claims);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer no-tenant-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void missingTier_returns401() {
        Claims claims = buildClaims("tenant-123", null);
        when(jwtService.validateToken("no-tier-token")).thenReturn(claims);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer no-tier-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidTier_returns401() {

        Claims claims = buildClaims("tenant-123", "DIAMOND");
        when(jwtService.validateToken("bad-tier-token")).thenReturn(claims);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer bad-tier-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidToken_jwtServiceThrows_returns401() {
        when(jwtService.validateToken("garbage-token"))
                .thenThrow(new RuntimeException("Invalid signature"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test/anything")
                        .header("Authorization", "Bearer garbage-token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filterOrder_isHighestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
    }
}