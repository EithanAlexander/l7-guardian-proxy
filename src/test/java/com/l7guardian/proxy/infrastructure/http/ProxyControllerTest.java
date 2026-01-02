package com.l7guardian.proxy.infrastructure.http;

import com.l7guardian.proxy.domain.model.ProxyRequest;
import com.l7guardian.proxy.domain.model.ProxyResponse;
import com.l7guardian.proxy.infrastructure.forward.ProxyForwarder;
import com.l7guardian.proxy.infrastructure.forward.ProxyForwardingException;
import com.l7guardian.proxy.mapper.ProxyRequestMapper;
import com.l7guardian.proxy.mapper.ProxyResponseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock
    private ProxyForwarder proxyForwarder;

    @Mock
    private ProxyRequestMapper requestMapper;

    @Mock
    private ProxyResponseMapper responseMapper;

    @InjectMocks
    private ProxyController controller;

    @Mock
    private HttpServletRequest servletRequest;

    @Test
    @DisplayName("Happy Path: Should orchestrate mapping, forwarding, and responding")
    void shouldHandleRequestSuccessfully() throws IOException {
        // Arrange
        ProxyRequest mockRequest = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        ProxyResponse mockResponse = new ProxyResponse(200, Map.of(), new byte[0]);
        ResponseEntity<byte[]> expectedEntity = ResponseEntity.ok().body(new byte[0]);

        // Mock the flow
        when(servletRequest.getHeader("X-Request-ID")).thenReturn("req-123");
        when(requestMapper.map(servletRequest)).thenReturn(mockRequest);
        when(proxyForwarder.forward(mockRequest)).thenReturn(mockResponse);
        when(responseMapper.toResponseEntity(mockResponse)).thenReturn(expectedEntity);

        // Act
        ResponseEntity<byte[]> result = controller.handle(servletRequest);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        verify(requestMapper).map(servletRequest);
        verify(proxyForwarder).forward(mockRequest);
        verify(responseMapper).toResponseEntity(mockResponse);
    }

    @Test
    @DisplayName("Error Handling: Should return 500 when Forwarder fails")
    void shouldReturn500OnForwardingFailure() throws IOException {
        // Arrange
        ProxyRequest mockRequest = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);

        when(requestMapper.map(servletRequest)).thenReturn(mockRequest);
        // Simulate a backend failure or logic error in the forwarder
        when(proxyForwarder.forward(mockRequest))
                .thenThrow(new ProxyForwardingException("Backend down", new RuntimeException()));

        // Act
        ResponseEntity<byte[]> result = controller.handle(servletRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        // Ensure we didn't try to map a null response
        verify(responseMapper, never()).toResponseEntity(any());
    }

    @Test
    @DisplayName("Edge Case: Should propagate IOException from RequestMapper")
    void shouldPropagateIOException() throws IOException {
        // Arrange
        // Simulate a failure reading the input stream (client disconnect, etc.)
        when(requestMapper.map(servletRequest)).thenThrow(new IOException("Stream closed"));

        // Act & Assert
        // The controller declares "throws IOException", so it should bubble up
        // to be handled by Spring's global error handler.
        assertThrows(IOException.class, () -> controller.handle(servletRequest));

        verify(proxyForwarder, never()).forward(any());
    }

    @Test
    @DisplayName("Sanitization: Should handle missing X-Request-ID without crashing")
    void shouldHandleMissingRequestId() throws IOException {
        // Arrange
        ProxyRequest mockRequest = new ProxyRequest("GET", "/test", Map.of(), new byte[0]);
        ProxyResponse mockResponse = new ProxyResponse(200, Map.of(), new byte[0]);
        ResponseEntity<byte[]> expectedEntity = ResponseEntity.ok().build();

        when(servletRequest.getHeader("X-Request-ID")).thenReturn(null); // Missing header
        when(requestMapper.map(servletRequest)).thenReturn(mockRequest);
        when(proxyForwarder.forward(mockRequest)).thenReturn(mockResponse);
        when(responseMapper.toResponseEntity(mockResponse)).thenReturn(expectedEntity);

        // Act
        ResponseEntity<byte[]> result = controller.handle(servletRequest);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        // Verify flow completed despite missing ID
        verify(proxyForwarder).forward(mockRequest);
    }
}
