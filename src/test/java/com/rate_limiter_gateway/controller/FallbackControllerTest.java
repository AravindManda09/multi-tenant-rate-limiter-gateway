package com.rate_limiter_gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void fallback_returns503() {
        ResponseEntity<Map<String, String>> response = controller.fallback();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void fallback_returnsCorrectJsonBody() {
        ResponseEntity<Map<String, String>> response = controller.fallback();

        assertNotNull(response.getBody());
        assertEquals("Service temporarily unavailable", response.getBody().get("error"));
    }

    @Test
    void fallback_contentTypeIsJson() {
        ResponseEntity<Map<String, String>> response = controller.fallback();

        assertNotNull(response.getHeaders().getContentType());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
    }
}