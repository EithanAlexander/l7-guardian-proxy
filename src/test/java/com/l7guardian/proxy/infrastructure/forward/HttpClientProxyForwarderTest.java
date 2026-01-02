package com.l7guardian.proxy.infrastructure.forward;


import com.l7guardian.proxy.domain.model.ProxyRequest;
import com.l7guardian.proxy.domain.model.ProxyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientProxyForwarderTest {

    private static final URI BACKEND_URI = URI.create("http://backend.example.com");
    private HttpClient mockHttpClient;
    private HttpClientProxyForwarder forwarder;


    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        forwarder = new HttpClientProxyForwarder(BACKEND_URI, mockHttpClient);
    }

    // --- URI Building Tests ---

    @ParameterizedTest(name = "Path ''{0}'' with backend ''{1}'' should create URI ''{2}''")
    @CsvSource({
            "/users,              http://backend.com,      http://backend.com/users",
            "/users,              http://backend.com/,     http://backend.com/users",
            "/api/v1/data,        http://backend.com,      http://backend.com/api/v1/data",
            "/,                   http://backend.com,      http://backend.com/",
            "/users?id=123,       http://backend.com,      http://backend.com/users?id=123"
    })
    @DisplayName("Should build correct target URI")
    void shouldBuildCorrectTargetUri(String path, String backend, String expectedUri) throws Exception {
        // Arrange
        forwarder = new HttpClientProxyForwarder(URI.create(backend), mockHttpClient);
        ProxyRequest request = new ProxyRequest("GET", path, Map.of(), new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals(expectedUri, captor.getValue().uri().toString());
    }

    // --- HTTP Method Tests ---

    @ParameterizedTest(name = "Should forward {0} request correctly")
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"})
    @DisplayName("Should forward all HTTP methods")
    void shouldForwardAllHttpMethods(String method) throws Exception {
        // Arrange
        byte[] body = method.equals("GET") || method.equals("HEAD")
                ? new byte[0]
                : "test body".getBytes(StandardCharsets.UTF_8);
        ProxyRequest request = new ProxyRequest(method, "/test", Map.of(), body);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals(method, captor.getValue().method());
    }

    // --- Header Filtering Tests ---

    @Test
    @DisplayName("Should filter out all restricted headers")
    void shouldFilterRestrictedHeaders() throws Exception {
        // Arrange
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Connection", List.of("keep-alive"));
        headers.put("Content-Length", List.of("123"));
        headers.put("Expect", List.of("100-continue"));
        headers.put("Host", List.of("example.com"));
        headers.put("Upgrade", List.of("websocket"));
        headers.put("Authorization", List.of("Bearer token")); // Should be kept

        ProxyRequest request = new ProxyRequest("GET", "/test", headers, new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest sentRequest = captor.getValue();

        assertFalse(sentRequest.headers().map().containsKey("Connection"));
        assertFalse(sentRequest.headers().map().containsKey("Content-Length"));
        assertFalse(sentRequest.headers().map().containsKey("Expect"));
        assertFalse(sentRequest.headers().map().containsKey("Host"));
        assertFalse(sentRequest.headers().map().containsKey("Upgrade"));
        assertTrue(sentRequest.headers().map().containsKey("Authorization"));
    }

    @Test
    @DisplayName("Should forward allowed headers with multiple values")
    void shouldForwardMultiValueHeaders() throws Exception {
        // Arrange
        Map<String, List<String>> headers = Map.of(
                "Accept", List.of("application/json", "text/plain"),
                "X-Custom", List.of("value1", "value2", "value3")
        );

        ProxyRequest request = new ProxyRequest("GET", "/test", headers, new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest sentRequest = captor.getValue();

        assertEquals(List.of("application/json", "text/plain"),
                sentRequest.headers().allValues("Accept"));
        assertEquals(List.of("value1", "value2", "value3"),
                sentRequest.headers().allValues("X-Custom"));
    }

    @ParameterizedTest(name = "Restricted header ''{0}'' should be filtered (case-insensitive)")
    @ValueSource(strings = {"connection", "CONNECTION", "CoNnEcTiOn", "host", "HOST"})
    @DisplayName("Should filter restricted headers case-insensitively")
    void shouldFilterRestrictedHeadersCaseInsensitive(String headerName) throws Exception {
        // Arrange
        Map<String, List<String>> headers = Map.of(headerName, List.of("value"));
        ProxyRequest request = new ProxyRequest("GET", "/test", headers, new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest sentRequest = captor.getValue();

        assertFalse(sentRequest.headers().map().containsKey(headerName));
    }

    // --- Body Handling Tests ---

    @Test
    @DisplayName("Should forward request body for POST")
    void shouldForwardRequestBody() throws Exception {
        // Arrange
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        ProxyRequest request = new ProxyRequest("POST", "/users", Map.of(), body);

        HttpResponse<byte[]> mockResponse = createMockResponse(201, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());

        // Verify body was sent (we can't easily extract it, but we can verify it's not empty)
        assertTrue(captor.getValue().bodyPublisher().isPresent());
    }

    @Test
    @DisplayName("Should handle empty body for GET request")
    void shouldHandleEmptyBodyForGet() throws Exception {
        // Arrange
        ProxyRequest request = new ProxyRequest("GET", "/users", Map.of(), new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        forwarder.forward(request);

        // Assert
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());

        // For GET with no body, bodyPublisher might still be present but empty
        assertNotNull(captor.getValue());
    }

    // --- Response Mapping Tests ---

    @ParameterizedTest(name = "Should map status code {0}")
    @ValueSource(ints = {200, 201, 204, 301, 400, 401, 403, 404, 500, 502, 503})
    @DisplayName("Should correctly map various status codes")
    void shouldMapStatusCodes(int statusCode) throws Exception {
        // Arrange
        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        HttpResponse<byte[]> mockResponse = createMockResponse(statusCode, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        ProxyResponse response = forwarder.forward(request);

        // Assert
        assertEquals(statusCode, response.statusCode());
    }

    @Test
    @DisplayName("Should map response headers correctly")
    void shouldMapResponseHeaders() throws Exception {
        // Arrange
        Map<String, List<String>> responseHeaders = Map.of(
                "Content-Type", List.of("application/json"),
                "X-Custom-Header", List.of("value1", "value2")
        );

        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        HttpResponse<byte[]> mockResponse = createMockResponse(200, responseHeaders, new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        ProxyResponse response = forwarder.forward(request);

        // Assert
        assertEquals(responseHeaders, response.headers());
    }

    @Test
    @DisplayName("Should map response body correctly")
    void shouldMapResponseBody() throws Exception {
        // Arrange
        byte[] responseBody = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), responseBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        ProxyResponse response = forwarder.forward(request);

        // Assert
        assertArrayEquals(responseBody, response.body());
    }

    // --- Exception Handling Tests ---

    @Test
    @DisplayName("Should wrap IOException in ProxyForwardingException")
    void shouldWrapIOException() throws Exception {
        // Arrange
        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        // Act & Assert
        ProxyForwardingException exception = assertThrows(
                ProxyForwardingException.class,
                () -> forwarder.forward(request)
        );

        assertTrue(exception.getMessage().contains("Upstream backend failure"));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should wrap InterruptedException and restore interrupt flag")
    void shouldWrapInterruptedException() throws Exception {
        // Arrange
        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Thread interrupted"));

        // Clear interrupt flag before test
        Thread.interrupted();

        // Act & Assert
        ProxyForwardingException exception = assertThrows(
                ProxyForwardingException.class,
                () -> forwarder.forward(request)
        );

        assertTrue(exception.getMessage().contains("Request interrupted"));
        assertInstanceOf(InterruptedException.class, exception.getCause());
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should be restored");

        // Clean up
        Thread.interrupted();
    }

    @Test
    @DisplayName("Should wrap IllegalArgumentException for invalid URIs")
    void shouldWrapIllegalArgumentException() throws Exception {
        // Arrange
        forwarder = new HttpClientProxyForwarder(URI.create("http://backend.com"), mockHttpClient);

        // Create a request with invalid characters that will cause URI.create to fail
        ProxyRequest request = new ProxyRequest("GET", "/path with spaces", Map.of(), new byte[0]);

        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act & Assert
        ProxyForwardingException exception = assertThrows(
                ProxyForwardingException.class,
                () -> forwarder.forward(request)
        );

        assertTrue(exception.getMessage().contains("Failed to build valid backend request"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    // --- Edge Cases ---

    @Test
    @DisplayName("Should handle empty headers map")
    void shouldHandleEmptyHeaders() throws Exception {
        // Arrange
        ProxyRequest request = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), new byte[0]);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        ProxyResponse response = forwarder.forward(request);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("Should handle large response body")
    void shouldHandleLargeResponseBody() throws Exception {
        // Arrange
        byte[] largeBody = new byte[1024 * 1024]; // 1MB
        ProxyRequest request = new ProxyRequest("GET", "/large", Map.of(), new byte[0]);
        HttpResponse<byte[]> mockResponse = createMockResponse(200, Map.of(), largeBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act
        ProxyResponse response = forwarder.forward(request);

        // Assert
        assertEquals(1024 * 1024, response.body().length);
    }

    // --- Helper Methods ---

    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> createMockResponse(int statusCode,
                                                    Map<String, List<String>> headers,
                                                    byte[] body) {
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(body);

        java.net.http.HttpHeaders mockHeaders = java.net.http.HttpHeaders.of(
                headers,
                (k, v) -> true
        );

        when(mockResponse.headers()).thenReturn(mockHeaders);
        return mockResponse;
    }
}