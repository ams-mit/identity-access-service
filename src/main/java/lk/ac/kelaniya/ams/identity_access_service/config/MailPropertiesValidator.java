package lk.ac.kelaniya.ams.identity_access_service.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates required SMTP mail configuration at application startup.
 * Fails fast with an IllegalStateException if required credentials are not configured,
 * following the same fail-fast design pattern as RsaKeyProvider.
 */
@Slf4j
@Component
@Getter
public class MailPropertiesValidator {

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    public MailPropertiesValidator() {
    }

    public MailPropertiesValidator(String username, String password, String host, int port) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
    }

    @PostConstruct
    public void validate() {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "SMTP username is not configured. Please set 'spring.mail.username' or environment variable SMTP_USERNAME.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "SMTP password is not configured. Please set 'spring.mail.password' or environment variable SMTP_PASSWORD or SMTP_APP_PASSWORD.");
        }
        log.info("SMTP configuration successfully validated at startup: host={}, port={}", host, port);
    }
}
