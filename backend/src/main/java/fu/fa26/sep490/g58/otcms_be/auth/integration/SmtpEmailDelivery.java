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
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpEmailDelivery implements EmailDeliveryService {
    private final JavaMailSender sender;
    private final EmailProperties properties;
    private final String passwordResetUrl;

    public SmtpEmailDelivery(JavaMailSender sender, EmailProperties properties) {
        this(sender, properties, "http://localhost:3000/reset-password");
    }

    @Autowired
    public SmtpEmailDelivery(JavaMailSender sender, EmailProperties properties,
                             @Value("${app.auth.password-reset-url}") String passwordResetUrl) {
        this.sender = sender;
        this.properties = properties;
        URI uri = URI.create(passwordResetUrl);
        boolean localHttp = "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !localHttp) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Password reset URL must be HTTPS (or loopback HTTP), without credentials, query or fragment");
        }
        this.passwordResetUrl = passwordResetUrl;
    }

    @Override
    public void sendVerificationCode(String normalizedEmail, String oneTimeCode, UUID accountId) {
        send(normalizedEmail, "OTCMS email verification", "Your verification code is: " + oneTimeCode);
    }

    @Override
    public void sendPasswordReset(String normalizedEmail, String resetToken, UUID accountId) {
        sendResetLink(normalizedEmail, resetToken, "patient");
    }

    @Override
    public void sendStaffPasswordReset(String normalizedEmail, String resetToken, UUID accountId) {
        sendResetLink(normalizedEmail, resetToken, "staff");
    }

    private void sendResetLink(String recipient, String token, String portal) {
        // The destination is trusted deployment configuration, never a request-provided redirect.
        String link = UriComponentsBuilder.fromUriString(passwordResetUrl)
                .queryParam("portal", portal).queryParam("token", "{token}")
                .encode().buildAndExpand(token).toUriString();
        send(recipient, "OTCMS - Đặt lại mật khẩu", "Mở liên kết sau để đặt mật khẩu mới:\n\n" + link
                + "\n\nLiên kết có hiệu lực trong 15 phút và chỉ sử dụng một lần."
                + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
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
