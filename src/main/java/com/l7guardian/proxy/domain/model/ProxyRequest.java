package com.l7guardian.proxy.domain.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Domain model for a proxied HTTP request.
 * <p>
 * Immutable and safe. Supports multi-value headers and optional body.
 * Designed for Phase 1 of the proxy: small body limit (e.g., 16KB),
 * normalized headers (lowercase keys, raw values), and basic HTTP compliance.
 * </p>
 */
public record ProxyRequest(
        String method,
        String path,
        Map<String, List<String>> headers,
        byte[] body
) {

    public static final String TRAILING_SLASH = "/";

    /**
     * Compact constructor.
     * <p>
     * Validates mandatory fields and creates a defensive copy of the body.
     * The headers map is wrapped in an unmodifiable map; each list is already immutable.
     * </p>
     *
     * @param method  HTTP method (GET, POST, etc.), mandatory
     * @param path    Request URI/path, mandatory
     * @param headers Normalized headers map, multi-value and immutable lists
     * @param body    Request body bytes, optional
     * @throws IllegalArgumentException if method or path is null/blank
     */
    public ProxyRequest {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("HTTP method is mandatory");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("URI/path is mandatory");
        }

        if (!path.startsWith(TRAILING_SLASH)) {
            path = TRAILING_SLASH + path;
        }

        // Wrap the map itself in an unmodifiable map
        headers = headers != null ? Map.copyOf(headers) : Map.of();

        // Defensive copy of body
        body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Factory method to create a ProxyRequest safely.
     * <p>
     * Normalizes header keys to lowercase, merges duplicate keys,
     * makes each header list immutable, and enforces a body size limit.
     * </p>
     *
     * @param method      HTTP method
     * @param uri         Request URI/path
     * @param rawHeaders  Raw headers map (may contain multiple values per key)
     * @param body        Request body bytes
     * @param maxBodySize Maximum allowed body size in bytes
     * @return ProxyRequest instance
     * @throws IllegalArgumentException if body exceeds maxBodySize
     */
    public static ProxyRequest of(String method,
                                  String uri,
                                  Map<String, List<String>> rawHeaders,
                                  byte[] body,
                                  long maxBodySize) {

        // 1. Enforce body size limit
        if (body != null && body.length > maxBodySize) {
            throw new IllegalArgumentException("Request body exceeds max allowed size: " + maxBodySize);
        }

        // 2. Normalize headers: lowercase keys, merge duplicates, make lists immutable in one pass
        Map<String, List<String>> normalizedHeaders = rawHeaders != null
                ? rawHeaders.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(
                        e -> e.getKey().toLowerCase(),           // normalize key
                        e -> List.copyOf(e.getValue()), // make each list immutable
                        (list1, list2) -> // merge duplicates
                                Stream.concat(list1.stream(), list2.stream())
                                        .toList())
                )
                : Collections.emptyMap();

        return new ProxyRequest(method, uri, normalizedHeaders, body);
    }

    /**
     * Helper to check if this request has a body.
     *
     * @return true if the body is non-empty
     */
    public boolean hasBody() {
        return body != null && body.length > 0;
    }

    /**
     * Overridden to use Arrays.equals() for the byte array.
     * Default record implementation uses reference equality (==) for arrays,
     * which is incorrect for value objects.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyRequest that)) return false;
        return Objects.equals(method, that.method) &&
                Objects.equals(path, that.path) &&
                Objects.equals(headers, that.headers) &&
                Arrays.equals(body, that.body); // Checks content, not reference
    }

    /**
     * Overridden to use Arrays.hashCode() for the byte array.
     * Ensures that two requests with the same content produce the same hash.
     */
    @Override
    public int hashCode() {
        int result = Objects.hash(method, path, headers);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    /**
     * Overridden to provide a safe string representation.
     * Note: Arrays.toString(body) would dump all bytes (bad for logs).
     * We print the size instead, which is safer but still acknowledges the field exists.
     */
    @Override
    public String toString() {
        return "ProxyRequest[" +
                "method=" + method + ", " +
                "path=" + path + ", " +
                "headers=" + headers + ", " +
                "body=" + (body == null ? "null" : "byte[" + body.length + "]") + "]";
    }
}
