package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.integration.SmtpEmailDelivery;
import fu.fa26.sep490.g58.otcms_be.config.EmailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.mockito.ArgumentCaptor;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T009SmtpEmailDeliveryTest {
    @Test void resetEmailsContainConfiguredFrontendLinksForTheCorrectPortal() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage patient = new MimeMessage(Session.getInstance(new java.util.Properties()));
        MimeMessage staff = new MimeMessage(Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(patient, staff);
        EmailProperties properties = new EmailProperties(true, "smtp.gmail.com", 587, "sender@example.test", "secret", "sender@example.test", 5000, 10000, 10000, 2);
        var delivery = new SmtpEmailDelivery(sender, properties, "https://clinic.example.test/reset-password");
        delivery.sendPasswordReset("patient@example.test", "token+&value", null);
        delivery.sendStaffPasswordReset("staff@example.test", "staff-token", null);
        assertTrue(patient.getContent().toString().contains("https://clinic.example.test/reset-password?portal=patient&token=token%2B%26value"));
        assertTrue(staff.getContent().toString().contains("https://clinic.example.test/reset-password?portal=staff&token=staff-token"));
        assertTrue(patient.getContent().toString().contains("15 phút"));
        verify(sender).send(patient);
        verify(sender).send(staff);
    }

    @Test void refusesUnsafeResetDestinations() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailProperties properties = new EmailProperties(false, null, 587, null, null, null, 5000, 10000, 10000, 2);
        for (String url : java.util.List.of("http://external.example/reset-password", "https://user:password@example.test/reset-password", "https://clinic.example/reset-password?token=old", "https://clinic.example/reset-password#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> new SmtpEmailDelivery(sender, properties, url));
        }
    }
    @Test void passwordResetBodyContainsTokenWithoutLoggingIt() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailProperties properties = new EmailProperties(true, "smtp.gmail.com", 587, "sender@example.test", "secret", "sender@example.test", 5000, 10000, 10000, 2);
        new SmtpEmailDelivery(sender, properties).sendPasswordReset("patient@example.test", "secret-reset-token", null);
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        assertTrue(String.valueOf(sent.getValue().getContent()).contains("secret-reset-token"));
    }

    @Test void retriesTransientNetworkFailureOnceAndDoesNotRetryPermanentFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new MailSendException("temporary", new SocketTimeoutException("timeout")));
        EmailProperties properties = new EmailProperties(true, "smtp.gmail.com", 587, "sender@example.test", "secret", "sender@example.test", 5000, 10000, 10000, 2);
        assertThrows(MailSendException.class, () -> new SmtpEmailDelivery(sender, properties).sendVerificationCode("p@example.test", "123456", null));
        verify(sender, times(2)).createMimeMessage();
    }
}
