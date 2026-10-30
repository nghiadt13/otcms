package fu.fa26.sep490.g58.otcms_be.auth.integration;

import fu.fa26.sep490.g58.otcms_be.config.EmailProperties;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpEmailDelivery implements EmailDeliveryService {
    private final JavaMailSender sender;
    private final EmailProperties properties;

    public SmtpEmailDelivery(JavaMailSender sender, EmailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String normalizedEmail, String oneTimeCode, UUID accountId) {
        send(normalizedEmail, "OTCMS email verification", "Your verification code is: " + oneTimeCode);
    }

    @Override
    public void sendPasswordReset(String normalizedEmail, String resetToken, UUID accountId) {
        send(normalizedEmail, "OTCMS password reset", "Use this password reset token in the OTCMS client: " + resetToken);
    }

    private void send(String recipient, String subject, String body) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(properties.from());
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(body, false);
                sender.send(message);
                return;
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt >= properties.maxAttempts() || !isTransient(ex)) throw ex;
            } catch (Exception ex) {
                last = new IllegalStateException("Email delivery failed", ex);
                if (attempt >= properties.maxAttempts() || !isTransient(ex)) throw last;
            }
        }
        throw last == null ? new IllegalStateException("Email delivery failed") : last;
    }

    private boolean isTransient(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException || current instanceof IOException) return true;
        }
        return false;
    }
}
