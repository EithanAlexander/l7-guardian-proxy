package com.l7guardian.proxy.infrastructure.guard;

import com.l7guardian.proxy.infrastructure.config.GuardianSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Guardian ingress filter.
 *
 * <p>
 * Phase 1 responsibility:
 * <ul>
 *   <li>Block all requests by default</li>
 *   <li>Allow only explicitly approved paths</li>
 * </ul>
 *
 * <p>
 * This filter executes BEFORE any proxy forwarding logic.
 * If a request is denied here, it will never reach the backend.
 */
@Component
public class GuardianFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GuardianFilter.class);

    private final ZeroAllocRouteTrie routeTrie;
    private static final byte[] BLOCKED_JSON = """
        {
          "error": "request_blocked",
          "reason": "path_not_allowed"
        }
        """.getBytes(StandardCharsets.UTF_8);

    /**
     * Injects allowed paths from application.yml.
     * <p>
     * Spring automatically splits the YAML list into this List<String>.
     */
    // specific injection of the configuration properties
    public GuardianFilter(GuardianSecurityProperties properties) {
        this.routeTrie = new ZeroAllocRouteTrie();

        // 1. Load routes from YAML
        if (properties.getAllowedPaths() != null) {
            for (String route : properties.getAllowedPaths()) {
                this.routeTrie.insert(route);
            }
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // HOT PATH: This is the only line that runs 50k times/sec.
        // It uses the pre-built Trie.
        if (!routeTrie.isAllowed(request.getRequestURI())) {
            log.debug("Blocked request [{} {}] - path not allowed",
                    request.getMethod(), request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getOutputStream().write(BLOCKED_JSON);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
