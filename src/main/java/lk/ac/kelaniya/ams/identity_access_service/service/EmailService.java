package lk.ac.kelaniya.ams.identity_access_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service managing automated email notifications for user lifecycle events.
 * Email dispatch is best-effort and non-blocking: any delivery failure is logged
 * without failing or rolling back the caller's transaction.
 */
@Slf4j
@Service
public class EmailService {

    public static final String SUBJECT_APPROVED = "Registration Approved - Apartment Management System";
    public static final String SUBJECT_REJECTED = "Registration Application Update - Apartment Management System";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@ams.lk}")
    private String fromEmail = "noreply@ams.lk";

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public EmailService(JavaMailSender mailSender, String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "noreply@ams.lk";
    }

    /**
     * Sends a plain-text email notifying an applicant of their registration outcome.
     * Execution is safely wrapped in try-catch so failures never throw or roll back
     * the administrative status transition.
     *
     * @param toEmail   recipient email address
     * @param firstName recipient first name (used for greeting)
     * @param approved  true if the registration was approved (ACTIVE), false if rejected (REJECTED)
     * @param reason    mandatory justification for rejection (ignored when approved is true)
     */
    public void sendRegistrationOutcomeEmail(String toEmail, String firstName, boolean approved, String reason) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Cannot send registration outcome email: recipient email is null or blank.");
            return;
        }

        try {
            String greetingName = (firstName != null && !firstName.isBlank()) ? firstName.trim() : "Resident";
            String subject;
            String body;

            if (approved) {
                subject = SUBJECT_APPROVED;
                body = String.format(
                        "Hello %s,%n%n"
                                + "Your registration request for the Apartment Management System has been approved.%n"
                                + "Your account is now active and you may log in to the portal.%n%n"
                                + "Thank you,%n"
                                + "Apartment Management Team",
                        greetingName
                );
            } else {
                subject = SUBJECT_REJECTED;
                String rejectionReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Not specified";
                body = String.format(
                        "Hello %s,%n%n"
                                + "Thank you for your interest in the Apartment Management System.%n"
                                + "We regret to inform you that your registration application was not approved.%n%n"
                                + "Reason: %s%n%n"
                                + "If you believe this decision was made in error or have questions, please contact the building administration.%n%n"
                                + "Thank you,%n"
                                + "Apartment Management Team",
                        greetingName,
                        rejectionReason
                );
            }

            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            log.info("Sending registration outcome email to '{}' (approved={})", toEmail, approved);
            mailSender.send(message);
            log.info("Registration outcome email successfully dispatched to '{}'", toEmail);

        } catch (Exception ex) {
            log.error("Failed to deliver registration outcome email to '{}': {}. "
                    + "The account status transition remains committed (best-effort notification).",
                    toEmail, ex.getMessage(), ex);
        }
    }
}
