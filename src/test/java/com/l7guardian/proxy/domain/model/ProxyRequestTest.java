package com.l7guardian.proxy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyRequestTest {

    // --- Constructor & Validation Tests ---

    @DisplayName("Constructor should throw exception for missing mandatory fields")
    @ParameterizedTest(name = "Invalid inputs: method={0}, path={1}")
    @CsvSource(value = {
            "null,  /api",   // Case 1: Method is null
            "'   ', /api",   // Case 2: Method is blank
            "GET,   null",   // Case 3: Path is null
            "GET,   '   '"   // Case 4: Path is blank
    }, nullValues = "null")
    void testConstructorValidation(String method, String path) {
        // Setup: Create the map outside the assertion scope
        Map<String, List<String>> emptyHeaders = Map.of();

        // Act & Assert: The lambda now contains ONLY the constructor call
        assertThrows(IllegalArgumentException.class, () ->
                new ProxyRequest(method, path, emptyHeaders, null)
        );
    }

    @Test
    @DisplayName("Constructor should handle null headers and body gracefully")
    void testConstructorNullSafety() {
        ProxyRequest request = new ProxyRequest("GET", "/api", null, null);

        assertNotNull(request.headers(), "Headers should default to empty map");
        assertTrue(request.headers().isEmpty());
        assertNotNull(request.body(), "Body should default to empty array");
        assertEquals(0, request.body().length);
    }

    // --- Immutability & Defensive Copy Tests ---

    @Test
    @DisplayName("Should create defensive copy of the body (Immutability)")
    void testBodyDefensiveCopy() {
        byte[] originalBody = "original".getBytes(StandardCharsets.UTF_8);

        // Create request
        ProxyRequest request = new ProxyRequest("POST", "/api", Map.of(), originalBody);

        // Mutate original array
        originalBody[0] = 'X';

        // Verify request body remained unchanged
        assertEquals("original", new String(request.body(), StandardCharsets.UTF_8));
        assertNotSame(originalBody, request.body());
    }

    @Test
    @DisplayName("Headers map should be immutable")
    void testHeadersImmutability() {
        Map<String, List<String>> rawHeaders = new HashMap<>();
        rawHeaders.put("Content-Type", new ArrayList<>(List.of("application/json")));

        ProxyRequest request = new ProxyRequest("GET", "/api", rawHeaders, null);

        // 1. Verify modifying original map doesn't affect request
        rawHeaders.put("New-Header", List.of("value"));
        assertFalse(request.headers().containsKey("New-Header"));

        // 2. Verify getter returns unmodifiable map
        assertThrows(UnsupportedOperationException.class, () ->
                request.headers().put("Malicious-Header", List.of("hack"))
        );
    }

    // --- Static Factory (logic) Tests ---

    @Test
    @DisplayName("Factory should enforce max body size")
    void testFactoryBodyLimit() {
        byte[] largeBody = new byte[100];
        long maxLimit = 50;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ProxyRequest.of("POST", "/api", null, largeBody, maxLimit)
        );

        assertTrue(ex.getMessage().contains("exceeds max allowed size"));
    }

    @Test
    @DisplayName("Factory should normalize header keys to lowercase")
    void testHeaderNormalization() {
        Map<String, List<String>> rawHeaders = Map.of(
                "Content-Type", List.of("application/json"),
                "USER-AGENT", List.of("Mozilla")
        );

        ProxyRequest request = ProxyRequest.of("GET", "/api", rawHeaders, null, 1000);

        assertTrue(request.headers().containsKey("content-type"));
        assertTrue(request.headers().containsKey("user-agent"));
        assertFalse(request.headers().containsKey("Content-Type")); // Old key gone
    }

    @Test
    @DisplayName("Factory should merge duplicate case-insensitive keys")
    void testHeaderMerging() {
        // Create a map that might come from a messy source (duplicate keys with diff casing)
        // Standard Map.of() doesn't allow dupes, so we use HashMap manually
        Map<String, List<String>> rawHeaders = new HashMap<>();
        rawHeaders.put("X-Custom", List.of("value1"));
        rawHeaders.put("x-custom", List.of("value2"));

        ProxyRequest request = ProxyRequest.of("GET", "/api", rawHeaders, null, 1000);

        // Should result in ONE key "x-custom" with BOTH values
        assertEquals(1, request.headers().size());
        List<String> values = request.headers().get("x-custom");

        // Note: Order depends on stream processing order, but usually safe to check containment
        assertEquals(2, values.size());
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }

    @Test
    @DisplayName("hasBody should return correct boolean")
    void testHasBody() {
        ProxyRequest reqWithBody = new ProxyRequest("POST", "/", null, new byte[1]);
        ProxyRequest reqEmpty = new ProxyRequest("GET", "/", null, new byte[0]);
        ProxyRequest reqNull = new ProxyRequest("GET", "/", null, null);

        assertTrue(reqWithBody.hasBody());
        assertFalse(reqEmpty.hasBody());
        assertFalse(reqNull.hasBody());
    }

    @Test
    @DisplayName("Should normalize paths missing leading slash")
    void shouldNormalizePath() {
        // Input "api/users" -> Stored as "/api/users"
        ProxyRequest req = new ProxyRequest("GET", "api/users", Map.of(), null);

        assertEquals("/api/users", req.path());
    }
}