package lk.ac.kelaniya.ams.identity_access_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailPropertiesValidatorTest {

    @Test
    @DisplayName("validate succeeds when valid SMTP username and password are provided")
    void testValidate_success() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "user@example.com", "secretpass", "smtp.gmail.com", 587);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate throws IllegalStateException when SMTP username is null or blank")
    void testValidate_missingUsername_throwsIllegalStateException() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "", "secretpass", "smtp.gmail.com", 587);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP username is not configured");
    }

    @Test
    @DisplayName("validate throws IllegalStateException when SMTP password is null or blank")
    void testValidate_missingPassword_throwsIllegalStateException() {
        MailPropertiesValidator validator = new MailPropertiesValidator(
                "user@example.com", "", "smtp.gmail.com", 587);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP password is not configured");
    }
}
