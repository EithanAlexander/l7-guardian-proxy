package com.l7guardian.proxy.mapper;

import com.l7guardian.proxy.domain.model.ProxyResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps a domain-level {@link ProxyResponse} to a Spring {@link ResponseEntity}.
 *
 * <p>
 * This class represents the egress boundary of the proxy system.
 * It is responsible for translating protocol-agnostic domain data
 * into framework-specific HTTP response constructs.
 * </p>
 *
 * <p>
 * Security note:
 * No header filtering or normalization is performed at this stage.
 * Such policies should be explicitly introduced when required
 * (e.g., stripping hop-by-hop headers, enforcing response limits).
 * </p>
 */
@Component
public class ProxyResponseMapper {

    /**
     * Converts a {@link ProxyResponse} into a Spring {@link ResponseEntity}.
     *
     * @param response the domain proxy response
     * @return a fully populated {@code ResponseEntity<byte[]>}
     */
    public ResponseEntity<byte[]> toResponseEntity(ProxyResponse response) {
        HttpHeaders httpHeaders = toHttpHeaders(response.headers());

        return ResponseEntity
                .status(response.statusCode())
                .headers(httpHeaders)
                .body(response.body());
    }

    /**
     * Converts a Map-based header representation into Spring {@link HttpHeaders}.
     *
     * @param headers domain response headers
     * @return HttpHeaders instance
     */
    private HttpHeaders toHttpHeaders(Map<String, List<String>> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::addAll);
        return httpHeaders;
    }
}
