package lk.ac.kelaniya.ams.identity_access_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@ams.lk");
    }

    @Test
    @DisplayName("sendRegistrationOutcomeEmail: approved=true constructs correct recipient, subject, and welcome body")
    void testSendRegistrationOutcomeEmail_approved_success() {
        String toEmail = "resident.john@ams.lk";
        String firstName = "John";

        emailService.sendRegistrationOutcomeEmail(toEmail, firstName, true, null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly(toEmail);
        assertThat(sentMessage.getFrom()).isEqualTo("noreply@ams.lk");
        assertThat(sentMessage.getSubject()).isEqualTo(EmailService.SUBJECT_APPROVED);
        assertThat(sentMessage.getText())
                .contains("Hello John,")
                .contains("approved")
                .contains("active");
    }

    @Test
    @DisplayName("sendRegistrationOutcomeEmail: approved=false constructs correct recipient, subject, and body with reason")
    void testSendRegistrationOutcomeEmail_rejected_success() {
        String toEmail = "applicant.jane@ams.lk";
        String firstName = "Jane";
        String reason = "Incomplete lease agreement documentation";

        emailService.sendRegistrationOutcomeEmail(toEmail, firstName, false, reason);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly(toEmail);
        assertThat(sentMessage.getFrom()).isEqualTo("noreply@ams.lk");
        assertThat(sentMessage.getSubject()).isEqualTo(EmailService.SUBJECT_REJECTED);
        assertThat(sentMessage.getText())
                .contains("Hello Jane,")
                .contains("not approved")
                .contains("Incomplete lease agreement documentation");
    }

    @Test
    @DisplayName("sendRegistrationOutcomeEmail: mailSender throwing MailSendException is caught and suppressed without throwing")
    void testSendRegistrationOutcomeEmail_mailSenderThrows_suppressesException() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendRegistrationOutcomeEmail(
                "user@ams.lk", "User", true, null
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendRegistrationOutcomeEmail: null or blank email does nothing and does not invoke mailSender")
    void testSendRegistrationOutcomeEmail_nullOrBlankEmail_doesNothing() {
        emailService.sendRegistrationOutcomeEmail(null, "John", true, null);
        emailService.sendRegistrationOutcomeEmail("   ", "John", true, null);

        verifyNoInteractions(mailSender);
    }
}
