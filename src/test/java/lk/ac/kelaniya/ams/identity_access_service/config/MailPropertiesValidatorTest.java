package lk.ac.kelaniya.ams.identity_access_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailPropertiesValidatorTest {

    @Test
    @DisplayName("validate succeeds when SMTP is enabled with valid username and password")
    void testValidate_success() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "user@example.com", "secretpass", "smtp.gmail.com", 587);

        setSmtpEnabled(validator, true);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate succeeds when SMTP is disabled without credentials")
    void testValidate_smtpDisabled_succeedsWithoutCredentials() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "", "", "smtp.gmail.com", 587);

        setSmtpEnabled(validator, false);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate throws when SMTP is enabled and username is missing")
    void testValidate_missingUsername_throwsIllegalStateException() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "", "secretpass", "smtp.gmail.com", 587);

        setSmtpEnabled(validator, true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP username is not configured");
    }

    @Test
    @DisplayName("validate throws when SMTP is enabled and password is missing")
    void testValidate_missingPassword_throwsIllegalStateException() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "user@example.com", "", "smtp.gmail.com", 587);

        setSmtpEnabled(validator, true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP password is not configured");
    }

    private void setSmtpEnabled(
            MailPropertiesValidator validator,
            boolean enabled) {

        try {
            Field field = MailPropertiesValidator.class
                    .getDeclaredField("smtpEnabled");

            field.setAccessible(true);
            field.set(validator, enabled);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to configure SMTP enabled state for test", e);
        }
    }
}
