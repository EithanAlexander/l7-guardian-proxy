package com.l7guardian.proxy.mapper;

import com.l7guardian.proxy.domain.model.ProxyRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;


/**
 * Maps HttpServletRequest to ProxyRequest domain model - the ingress boundary of the proxy
 */
@Component
public class ProxyRequestMapper {

    private static final Logger log = LoggerFactory.getLogger(ProxyRequestMapper.class);

    private final long maxBodySize;

    public ProxyRequestMapper(@Value("${proxy.max-body-size}") long maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    /**
     * Maps an incoming HttpServletRequest to a ProxyRequest.
     *
     * @param request the HttpServletRequest from a Spring Controller
     * @return a ProxyRequest instance
     * @throws IOException if reading the request body fails
     */
    public ProxyRequest map(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        Map<String, List<String>> headers = extractHeaders(request);
        byte[] body = readBody(request);

        return ProxyRequest.of(method, path, headers, body, maxBodySize);
    }

    /**
     * Eagerly copies headers from the servlet container into a Map.
     * <p>
     * Note: Header names are returned exactly as provided by the servlet container
     * (case-sensitive). No normalization is performed at this stage to preserve
     * original request semantics. Normalization, filtering, or security validation
     * must be applied in later pipeline stages.
     * </p>
     *
     * @param request The source HTTP request.
     * @return A map where each header name points to a list of values.
     */
    private Map<String, List<String>> extractHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames == null) {
            log.debug("Incoming request has no headers: [{} {}]",
                    request.getMethod(), request.getRequestURI());
            return headers;
        }

        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            headers.put(name, values != null ? Collections.list(values) : List.of());
        }
        return headers;
    }


    /**
     * Reads the request body into a byte array with a maximum size limit.
     *
     * @param request HttpServletRequest
     * @return byte[] body
     * @throws IOException if reading fails or exceeds max size
     */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        try (var input = request.getInputStream();
             var buffer = new ByteArrayOutputStream()) {

            byte[] chunk = new byte[1024];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = input.read(chunk)) != -1) {
                totalRead += bytesRead;
                if (totalRead > maxBodySize) {
                    throw new IllegalArgumentException(
                            "Request body exceeds max allowed size: " + maxBodySize
                    );
                }
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }
}
