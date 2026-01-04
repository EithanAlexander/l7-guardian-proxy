package com.l7guardian.proxy.infrastructure.http;


import com.l7guardian.proxy.core.exception.RequestPayloadTooLargeException;
import com.l7guardian.proxy.domain.model.ProxyRequest;
import com.l7guardian.proxy.infrastructure.forward.ProxyForwarder;
import com.l7guardian.proxy.infrastructure.forward.ProxyForwardingException;
import com.l7guardian.proxy.domain.model.ProxyResponse;
import com.l7guardian.proxy.mapper.ProxyRequestMapper;
import com.l7guardian.proxy.mapper.ProxyResponseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Entry point for all incoming HTTP traffic.
 *
 * <p><strong>Phase 1 (Skeleton) responsibilities:</strong></p>
 * <ul>
 *     <li>Accept all HTTP requests</li>
 *     <li>Extract minimal execution context (method, URI, request ID)</li>
 *     <li>Delegate forwarding logic to {@link ProxyForwarder}</li>
 *     <li>Return the backend response transparently</li>
 * </ul>
 *
 * <p>
 * This controller is intentionally thin:
 * it contains no routing, load-balancing, retry, or backend-selection logic.
 * </p>
 */
@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final ProxyForwarder proxyForwarder;
    private final ProxyRequestMapper proxyRequestMapper;
    private final ProxyResponseMapper responseMapper;

    public ProxyController(ProxyForwarder proxyForwarder, ProxyRequestMapper proxyRequestMapper, ProxyResponseMapper responseMapper) {
        this.proxyForwarder = proxyForwarder;
        this.proxyRequestMapper = proxyRequestMapper;
        this.responseMapper = responseMapper;
    }

    /**
     * Catch-all handler for all incoming HTTP requests.
     *
     * <p>
     * Security notes:
     * <ul>
     *     <li>No trust is placed in client-provided headers</li>
     *     <li>Request identifiers are logged only if present</li>
     *     <li>No sensitive headers are logged</li>
     * </ul>
     * </p>
     *
     * @param servletRequest the incoming servlet request
     * @return proxied response from the backend
     */
    @RequestMapping("/**")
    public ResponseEntity<byte[]> handle(HttpServletRequest servletRequest) throws IOException {

        Thread thread = Thread.currentThread();
        String requestId = sanitizeHeader(servletRequest.getHeader("X-Request-ID"));

        log.info(
                "Incoming request: requestId={} method={} uri={} thread={} isVirtual={}",
                requestId,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                thread.getName(),
                thread.isVirtual()
        );

        try {

            // 1. Map servlet request → domain
            ProxyRequest proxyRequest = proxyRequestMapper.map(servletRequest);

            // 2. Forward request
            ProxyResponse proxyResponse = proxyForwarder.forward(proxyRequest);

            // 3. Map domain response → HTTP response
            return responseMapper.toResponseEntity(proxyResponse);

        } catch (RequestPayloadTooLargeException ex) {
            // e.g. request body exceeds max allowed size
            log.warn(
                    "Rejected request: requestId={} method={} uri={} reason={}",
                    requestId,
                    servletRequest.getMethod(),
                    servletRequest.getRequestURI(),
                    ex.getMessage()
            );

            return ResponseEntity
                    .status(413)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"payload_too_large\"}".getBytes(StandardCharsets.UTF_8));


            } catch (ProxyForwardingException ex) {
            log.error(
                    "Failed to forward request to backend: requestId={} method={} uri={}",
                    requestId,
                    servletRequest.getMethod(),
                    servletRequest.getRequestURI(),
                    ex
            );

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }

    /**
     * Performs minimal sanitization on incoming header values to prevent
     * log injection and accidental propagation of malformed identifiers.
     *
     * @param value raw header value
     * @return sanitized value or {@code "unknown"} if empty
     */
    private String sanitizeHeader(String value) {
        return Optional.ofNullable(value)
                .map(v -> v.replaceAll("[\\r\\n]", ""))
                .filter(v -> !v.isBlank())
                .orElse("unknown");
    }
}

