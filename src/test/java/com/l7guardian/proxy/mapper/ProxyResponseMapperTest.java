package com.l7guardian.proxy.mapper;


import com.l7guardian.proxy.domain.model.ProxyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyResponseMapperTest {

    private final ProxyResponseMapper mapper = new ProxyResponseMapper();

    @Test
    @DisplayName("Should map status, headers, and body correctly")
    void shouldMapCompleteResponse() {
        // Arrange
        byte[] body = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "X-Custom-ID", List.of("999")
        );

        ProxyResponse proxyResponse = new ProxyResponse(201, headers, body);

        // Act
        ResponseEntity<byte[]> responseEntity = mapper.toResponseEntity(proxyResponse);

        // Assert
        assertEquals(201, responseEntity.getStatusCode().value());
        assertArrayEquals(body, responseEntity.getBody());

        assertEquals("application/json", responseEntity.getHeaders().getFirst("Content-Type"));
        assertEquals("999", responseEntity.getHeaders().getFirst("X-Custom-ID"));
    }

    @Test
    @DisplayName("Should correctly map multi-value headers")
    void shouldHandleMultiValueHeaders() {
        // Arrange
        Map<String, List<String>> headers = Map.of(
                "Set-Cookie", List.of("session=123", "theme=dark")
        );
        ProxyResponse proxyResponse = new ProxyResponse(200, headers, new byte[0]);

        // Act
        ResponseEntity<byte[]> responseEntity = mapper.toResponseEntity(proxyResponse);

        // Assert
        List<String> cookies = responseEntity.getHeaders().get("Set-Cookie");
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertTrue(cookies.contains("session=123"));
        assertTrue(cookies.contains("theme=dark"));
    }

    @Test
    @DisplayName("Should handle empty body and headers")
    void shouldHandleEmptyResponse() {
        // Arrange
        ProxyResponse proxyResponse = new ProxyResponse(204, Map.of(), new byte[0]);

        // Act
        ResponseEntity<byte[]> responseEntity = mapper.toResponseEntity(proxyResponse);

        // Assert
        assertEquals(204, responseEntity.getStatusCode().value());
        assertEquals(0, responseEntity.getBody().length);
        assertTrue(responseEntity.getHeaders().isEmpty());
    }

    @Test
    @DisplayName("Should merge headers with same name but different casing")
    void shouldMergeCaseInsensitiveHeaders() {
        // Arrange: Create a Map with "duplicate" keys (by case)
        // Standard Map allows this; HttpHeaders does not.
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-test-header", List.of("value1"));
        headers.put("X-TEST-HEADER", List.of("value2"));

        ProxyResponse proxyResponse = new ProxyResponse(200, headers, new byte[0]);

        // Act
        ResponseEntity<byte[]> response = mapper.toResponseEntity(proxyResponse);

        // Assert: Spring HttpHeaders should have merged them into one logical header
        List<String> values = response.getHeaders().get("x-test-header"); // Lookup is case-insensitive

        assertNotNull(values);
        assertEquals(2, values.size());
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }

    @Test
    @DisplayName("Should handle headers with empty value lists")
    void shouldHandleEmptyHeaderValues() {
        // Arrange
        Map<String, List<String>> headers = Map.of(
                "X-Empty-Header", List.of()
        );
        ProxyResponse proxyResponse = new ProxyResponse(200, headers, new byte[0]);

        // Act
        ResponseEntity<byte[]> response = mapper.toResponseEntity(proxyResponse);

        // Assert
        assertTrue(response.getHeaders().containsKey("X-Empty-Header"));
        assertTrue(response.getHeaders().get("X-Empty-Header").isEmpty());
    }

    @Test
    @DisplayName("Should preserve integrity of large binary bodies")
    void shouldPreserveLargeBinaryBody() {
        // Arrange: 1MB of random-ish data
        byte[] largeBody = new byte[1024 * 1024];
        largeBody[0] = 0xA;
        largeBody[largeBody.length - 1] = 0xB;

        ProxyResponse proxyResponse = new ProxyResponse(200, Map.of(), largeBody);

        // Act
        ResponseEntity<byte[]> response = mapper.toResponseEntity(proxyResponse);

        // Assert
        assertEquals(1024 * 1024, response.getBody().length);
        assertEquals(0xA, response.getBody()[0]);
        assertEquals(0xB, response.getBody()[largeBody.length - 1]);
    }
}
