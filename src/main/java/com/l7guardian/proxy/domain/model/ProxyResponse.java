package com.l7guardian.proxy.domain.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model for an HTTP response received from the backend.
 * <p>
 * Immutable and safe. Wraps the status code, headers, and body bytes.
 * </p>
 */
public record ProxyResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {

    /**
     * Compact constructor for validation and immutability.
     * Ensures headers are unmodifiable and the body is defensively copied.
     */
    public ProxyResponse {
        // 1. Safety: Wrap headers in an immutable map (or empty if null)
        headers = headers != null ? Map.copyOf(headers) : Map.of();

        // 2. Safety: Defensive copy of the byte array to prevent external mutation
        body = body != null ? body.clone() : new byte[0];
    }

    public boolean hasBody() {
        return body != null && body.length > 0;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyResponse(int code, Map<String, List<String>> headers1, byte[] body1))) return false;
        return statusCode == code &&
                Objects.equals(headers, headers1) &&
                Arrays.equals(body, body1); // Checks actual content, not reference
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(statusCode, headers);
        result = 31 * result + Arrays.hashCode(body); // Hashes actual content
        return result;
    }

    @Override
    public String toString() {
        return "ProxyResponse[" +
                "statusCode=" + statusCode + ", " +
                "headers=" + headers + ", " +
                "body=" + (body == null ? "null" : "byte[" + body.length + "]") + "]";
    }
}