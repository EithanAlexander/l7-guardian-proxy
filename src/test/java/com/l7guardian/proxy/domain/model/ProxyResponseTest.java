package com.l7guardian.proxy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ProxyResponse} record.
 * <p>
 * Ensures that the response model maintains immutability, safely handles null inputs,
 * and correctly implements value equality (especially for the byte array body).
 * </p>
 */
class ProxyResponseTest {


    @Test
    @DisplayName("Should maintain immutability by copying body and headers")
    void shouldDefensivelyCopyBodyAndHeaders() {
        byte[] originalBody = "test".getBytes();
        Map<String, List<String>> originalHeaders = new HashMap<>();
        originalHeaders.put("X-Test", List.of("value"));

        var response = new ProxyResponse(200, originalHeaders, originalBody);

        // Mutate originals
        originalBody[0] = 'X';
        originalHeaders.put("X-Evil", List.of("hacked"));

        // Verify response is unaffected
        assertEquals("test", new String(response.body()));
        assertFalse(response.headers().containsKey("X-Evil"));
    }


    @Test
    @DisplayName("Should handle null inputs by defaulting to empty structures")
    void shouldHandleNullInputs() {
        var response = new ProxyResponse(404, null, null);

        assertTrue(response.headers().isEmpty());
        assertEquals(0, response.body().length);
        assertFalse(response.hasBody());
    }


    @Test
    @DisplayName("Should compare equality based on byte array content")
    void shouldCompareByteArrayContent() {
        var resp1 = new ProxyResponse(200, Map.of("k", List.of("v")), new byte[]{1, 2, 3});
        var resp2 = new ProxyResponse(200, Map.of("k", List.of("v")), new byte[]{1, 2, 3});
        var resp3 = new ProxyResponse(200, Map.of("k", List.of("v")), new byte[]{9, 9, 9});

        assertEquals(resp1, resp2);
        assertEquals(resp1.hashCode(), resp2.hashCode());
        assertNotEquals(resp1, resp3);
    }


    @Test
    @DisplayName("Should return false for hasBody() when array is empty")
    void hasBodyShouldReturnFalseForEmptyArray() {
        var response = new ProxyResponse(200, Map.of(), new byte[0]);
        assertFalse(response.hasBody());
    }
}