package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for rate limiting and proxy IP resolution.
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitingProperties {

    /**
     * List of trusted proxy IP addresses or CIDR blocks (e.g. 127.0.0.1, 10.0.0.0/8, 172.16.0.0/12).
     * Empty by default (env var RATE_LIMIT_TRUSTED_PROXIES).
     * When request.getRemoteAddr() matches an entry in this list, client IP is resolved from X-Forwarded-For.
     */
    private List<String> trustedProxies = new ArrayList<>();
}
