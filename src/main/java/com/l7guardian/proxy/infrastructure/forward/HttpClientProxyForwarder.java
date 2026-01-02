package com.l7guardian.proxy.infrastructure.forward;

import com.l7guardian.proxy.domain.model.ProxyRequest;
import com.l7guardian.proxy.domain.model.ProxyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1 implementation of a ProxyForwarder.
 * Forwards ProxyRequest to a single configured backend and returns the backend's response.
 * <p>
 * This class handles:
 * <ul>
 *     <li>HTTP method and path</li>
 *     <li>Multi-value headers</li>
 *     <li>Request body forwarding</li>
 *     <li>Safety against restricted headers</li>
 * </ul>
 * <p>
 * It intentionally does NOT handle:
 * <ul>
 *     <li>Load balancing</li>
 *     <li>Retries</li>
 *     <li>Timeout tuning (beyond defaults)</li>
 *     <li>Streaming responses</li>
 * </ul>
 */
@Component
public class HttpClientProxyForwarder implements ProxyForwarder {

    private static final Logger log = LoggerFactory.getLogger(HttpClientProxyForwarder.class);

    // Headers that are restricted by HttpClient
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade"
    );

    private final HttpClient httpClient;
    private final URI backendBaseUri;

    @VisibleForTesting
    HttpClientProxyForwarder(URI backendBaseUri, HttpClient httpClient) {
        this.backendBaseUri = backendBaseUri;
        this.httpClient = httpClient;
    }

    /**
     * Constructor for Phase 1.
     * The backend URI is injected from Spring properties for configuration flexibility.
     *
     * @param backendBaseUri the base URI of the backend to forward requests to
     */
    @Autowired
    public HttpClientProxyForwarder(@Value("${proxy.backend.uri}") URI backendBaseUri) {
        this.backendBaseUri = backendBaseUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Forwards the given ProxyRequest to the configured backend and returns a ProxyResponse.
     *
     * @param request the validated proxy request
     * @return the response from the backend
     * @throws ProxyForwardingException in case of network failure, invalid headers, or interrupted execution
     */
    @Override
    public ProxyResponse forward(ProxyRequest request) {
        try {
            log.debug("Forwarding request: {} {}", request.method(), request.path());

            HttpRequest httpRequest = buildHttpRequest(request);
            HttpResponse<byte[]> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            return mapToProxyResponse(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyForwardingException("Request interrupted during forwarding", e);
        } catch (IOException e) {
            throw new ProxyForwardingException("Upstream backend failure: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ProxyForwardingException("Failed to build valid backend request", e);
        }
    }

    /**
     * Builds an HttpRequest from a ProxyRequest.
     * Filters out restricted headers and applies request body if present.
     *
     * @param request the ProxyRequest
     * @return the built HttpRequest
     */
    private HttpRequest buildHttpRequest(ProxyRequest request) {
        String rawPath = request.path(); // e.g., "/users"
        String base = backendBaseUri.toString().replaceAll("/$", ""); // remove trailing slash
        URI targetUri = URI.create(base + rawPath);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(10));

        HttpRequest.BodyPublisher publisher = request.hasBody()
                ? HttpRequest.BodyPublishers.ofByteArray(request.body())
                : HttpRequest.BodyPublishers.noBody();

        builder.method(request.method(), publisher);

        // Forward headers, filtering restricted ones
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            String keyLower = entry.getKey().toLowerCase();
            if (!RESTRICTED_HEADERS.contains(keyLower)) {
                entry.getValue().forEach(value -> builder.header(entry.getKey(), value));
            }
        }
        return builder.build();
    }

    /**
     * Maps HttpClient's HttpResponse to the domain ProxyResponse.
     *
     * @param response the HttpResponse from backend
     * @return a ProxyResponse containing status, headers, and body
     */
    private ProxyResponse mapToProxyResponse(HttpResponse<byte[]> response) {
        return new ProxyResponse(
                response.statusCode(),
                response.headers().map(),
                response.body()
        );
    }
}
