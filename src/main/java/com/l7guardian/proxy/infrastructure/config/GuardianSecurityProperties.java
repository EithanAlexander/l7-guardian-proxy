package com.l7guardian.proxy.infrastructure.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Configuration
@Validated
@ConfigurationProperties(prefix = "proxy.guardian.security")
public class GuardianSecurityProperties {

    /**
     * List of URL prefixes that are allowed to pass through the proxy.
     * Example: /api/v1, /auth, /public
     */
    @NotEmpty(message = "Must include at least one allowed path.")
    private List<String> allowedPaths = new ArrayList<>();

}
