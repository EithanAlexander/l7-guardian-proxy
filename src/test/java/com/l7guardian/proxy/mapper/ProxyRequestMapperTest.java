package com.l7guardian.proxy.mapper;

import com.l7guardian.proxy.core.exception.RequestPayloadTooLargeException;
import com.l7guardian.proxy.domain.model.ProxyRequest;
import jakarta.servlet.ServletInputStream;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;


import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProxyRequestMapper}.
 * <p>
 * Verifies that standard Servlet requests are correctly transformed into
 * the internal {@link ProxyRequest} domain model, including body limits
 * and header extraction.
 * </p>
 */
class ProxyRequestMapperTest {

    // Set a small limit (e.g., 50 bytes) to make testing limits easier
    private static final long TEST_MAX_BODY_SIZE = 50;

    private final ProxyRequestMapper mapper = new ProxyRequestMapper(TEST_MAX_BODY_SIZE);

    @Test
    @DisplayName("Should map valid request with body and headers")
    void shouldMapValidRequest() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-Request-ID", "12345");

        String jsonBody = "{\"user\":\"admin\"}";
        request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertEquals("POST", result.method());
        assertEquals("/api/v1/users", result.path());

        // Verify headers (Note: ProxyRequest.of() normalizes keys to lowercase)
        assertTrue(result.headers().containsKey("content-type"));
        assertTrue(result.headers().containsKey("x-request-id"));
        assertEquals("12345", result.headers().get("x-request-id").getFirst());

        // Verify Body
        assertArrayEquals(jsonBody.getBytes(StandardCharsets.UTF_8), result.body());
    }

    @ParameterizedTest(name = "Should map {0} request")
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"})
    @DisplayName("Should map all HTTP methods")
    void shouldMapAllHttpMethods(String method) throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/test");

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertEquals(method, result.method());
        assertEquals("/test", result.path());
    }

    @Test
    @DisplayName("Should throw exception when body exceeds configured max size")
    void shouldThrowOnBodySizeLimit() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");

        // Create a body larger than TEST_MAX_BODY_SIZE (50 bytes)
        String hugeBody = "A".repeat((int) TEST_MAX_BODY_SIZE + 10);
        request.setContent(hugeBody.getBytes(StandardCharsets.UTF_8));

        // Act & Assert
        RequestPayloadTooLargeException exception = assertThrows(RequestPayloadTooLargeException.class, () ->
                mapper.map(request)
        );

        assertThat(exception)
                .isInstanceOf(RequestPayloadTooLargeException.class)
                .hasMessageContaining("exceeds max allowed size");

    }

    @Test
    @DisplayName("Should accept body exactly at max size")
    void shouldAcceptBodyAtMaxSize() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");

        // Create a body exactly at TEST_MAX_BODY_SIZE (50 bytes)
        String bodyAtLimit = "A".repeat((int) TEST_MAX_BODY_SIZE);
        request.setContent(bodyAtLimit.getBytes(StandardCharsets.UTF_8));

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertEquals(TEST_MAX_BODY_SIZE, result.body().length);
    }

    @Test
    @DisplayName("Should handle empty body gracefully")
    void shouldHandleEmptyBody() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        // No content set

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertNotNull(result.body());
        assertEquals(0, result.body().length);
        assertFalse(result.hasBody());
    }

    @Test
    @DisplayName("Should normalize header names to lowercase")
    void shouldNormalizeHeaderNames() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-Custom-Header", "value");

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertTrue(result.headers().containsKey("content-type"));
        assertTrue(result.headers().containsKey("x-custom-header"));
        assertFalse(result.headers().containsKey("Content-Type"));
    }

    @Test
    @DisplayName("Should extract multi-value headers correctly")
    void shouldHandleMultiValueHeaders() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("X-Custom-List", "Value1");
        request.addHeader("X-Custom-List", "Value2");

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertTrue(result.headers().containsKey("x-custom-list"));
        var values = result.headers().get("x-custom-list");
        assertEquals(2, values.size());
        assertTrue(values.contains("Value1"));
        assertTrue(values.contains("Value2"));
    }

    @Test
    @DisplayName("Should handle request with no headers")
    void shouldHandleNoHeaders() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
        // No headers added

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertNotNull(result.headers());
        assertTrue(result.headers().isEmpty());
    }

    @Test
    @DisplayName("Should preserve exact body bytes (binary safety)")
    void shouldPreserveBinaryBody() throws IOException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/binary");
        byte[] binaryData = new byte[] { 1, 2, 0, -1, -50, 127 };
        request.setContent(binaryData);

        // Act
        ProxyRequest result = mapper.map(request);

        // Assert
        assertArrayEquals(binaryData, result.body());
    }

    @Test
    @DisplayName("Should propagate IOException when reading body fails")
    void shouldPropagateIOException() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test") {
            @SneakyThrows
            @Override
            public @NotNull ServletInputStream getInputStream() {
                throw new IOException("Stream read error");
            }
        };

        // Act & Assert
        assertThrows(IOException.class, () -> mapper.map(request));
    }
}
