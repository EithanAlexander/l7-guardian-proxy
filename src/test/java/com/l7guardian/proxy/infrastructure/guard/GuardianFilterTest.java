package com.l7guardian.proxy.infrastructure.guard;

import com.l7guardian.proxy.infrastructure.config.GuardianSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Test suite for the {@link GuardianFilter}.
 * <p>
 * This class verifies the security filtering logic powered by the {@link ZeroAllocRouteTrie}.
 * It ensures that requests matching allowed prefixes (defined in properties) pass through,
 * while unauthorized paths are rejected immediately with a 403 Forbidden status.
 * </p>
 */
@DisplayName("Guardian Filter Security Tests")
class GuardianFilterTest {

    private GuardianFilter filter;

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain filterChain = mock(FilterChain.class);

    // We capture the byte output written to the response here
    private final ByteArrayOutputStream responseOutputCaptor = new ByteArrayOutputStream();

    /**
     * Sets up the test environment before each test execution.
     * <p>
     * 1. Configures GuardianSecurityProperties with test paths.
     * 2. Initializes the filter (which builds the internal Trie).
     * 3. Mocks Servlet streams to capture JSON error responses.
     * </p>
     */
    @BeforeEach
    void setUp() throws IOException {
        GuardianSecurityProperties properties = new GuardianSecurityProperties();
        properties.setAllowedPaths(List.of("/test", "/actuator/health"));
        // 2. Initialize Filter
        filter = new GuardianFilter(properties);

        ServletOutputStream mockOutputStream = new ServletOutputStream() {
            @Override
            public void write(int b) {
                responseOutputCaptor.write(b);
            }

            @Override
            public boolean isReady() { return true; }

            @Override
            public void setWriteListener(WriteListener writeListener) {// must implement
                }
        };

        when(response.getOutputStream()).thenReturn(mockOutputStream);
    }

    /**
     * Verifies that various allowed paths (exact matches, sub-paths, and deep nested config)
     * are correctly permitted by the filter.
     * <p>
     * Replaces multiple individual tests to reduce code duplication (SonarQube).
     * </p>
     */
    @DisplayName("Should allow valid paths (Parameterized)")
    @ParameterizedTest(name = "Path allowed: {0}")
    @ValueSource(strings = {
            "/test",                // Exact match
            "/test/",               // Trailing slash
            "/test/some/resource",  // Sub-path (Prefix semantics)
            "/actuator/health"      // Deep nested specific config
    })
    void shouldAllowValidPaths(String path) throws Exception {
        when(request.getRequestURI()).thenReturn(path);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        // Verify the filter chain continued execution (Request Allowed)
        verify(filterChain, times(1)).doFilter(request, response);

        // Ensure no error block was triggered
        assertEquals(0, responseOutputCaptor.size());
    }


    /**
     * Verifies that various invalid paths (unknown, double slashes, partial matches)
     * are correctly blocked by the filter.
     * <p>
     * Replaces multiple individual blocking tests to reduce code duplication.
     * </p>
     */
    @DisplayName("Should block invalid paths (Parameterized)")
    @ParameterizedTest(name = "Path blocked: {0}")
    @ValueSource(strings = {
            "/unknown",   // Totally unknown path
            "//test",     // Double slash evasion attempt
            "/testing"    // Partial string match (prefix mismatch, e.g. /testing vs /test)
    })
    void shouldBlockInvalidPaths(String invalidPath) throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn(invalidPath);
        // Method type doesn't affect path matching logic, so we can use a standard GET
        when(request.getMethod()).thenReturn("GET");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then

        // 1. Verify the filter chain was HALTED (never called)
        verify(filterChain, never()).doFilter(request, response);

        // 2. Verify Status 403 Forbidden
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");

        // 3. Verify JSON body contains standard error structure
        String responseBody = responseOutputCaptor.toString();
        assertTrue(responseBody.contains("request_blocked"), "Response should contain error code");
        assertTrue(responseBody.contains("path_not_allowed"), "Response should contain reason");
    }

    /**
     * Verifies that the filter correctly applies **prefix-based allow rules**.
     * <p>
     * Scenario:
     * - The allowed path prefix is "/test".
     * - The requested URI is a deep nested path under that prefix.
     * <p>
     * Expected behavior:
     * - The request is allowed to continue through the filter chain.
     * - No error response is written.
     */
    @Test
    @DisplayName("Should allow deep path once prefix is matched")
    void shouldAllowDeepPathAfterPrefixMatch() throws Exception {
        // Given: a deeply nested path under the allowed prefix
        when(request.getRequestURI()).thenReturn("/test/very/deep/path/indeed/it/is");
        when(request.getMethod()).thenReturn("GET");

        // When: the filter processes the request
        filter.doFilterInternal(request, response, filterChain);

        // Then: request is allowed through the chain
        verify(filterChain).doFilter(request, response);

        // And: nothing is written to the response body (no blocking)
        assertEquals(0, responseOutputCaptor.size());
    }


}