package kln.ams.identityaccess.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized password policy definition and validation.
 * Enforces standard Project A password rules configured via application.yml:
 * - auth.password-policy.min-length (default 8)
 * - auth.password-policy.max-length (default 100)
 * - auth.password-policy.require-digit (default true)
 */
@Component
@ConfigurationProperties(prefix = "auth.password-policy")
@Getter
@Setter
public class PasswordPolicy {

    public static final String POLICY_MESSAGE = "Password must be at least 8 characters long and contain at least one numeric digit";

    private int minLength = 8;
    private int maxLength = 100;
    private boolean requireDigit = true;

    private static PasswordPolicy instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }

        PasswordPolicy policy = instance;
        int min = policy != null ? policy.getMinLength() : 8;
        int max = policy != null ? policy.getMaxLength() : 100;
        boolean reqDigit = policy == null || policy.isRequireDigit();

        if (password.length() < min || password.length() > max) {
            return false;
        }

        if (reqDigit) {
            boolean hasDigit = false;
            for (char c : password.toCharArray()) {
                if (Character.isDigit(c)) {
                    hasDigit = true;
                    break;
                }
            }
            if (!hasDigit) {
                return false;
            }
        }

        return true;
    }
}
