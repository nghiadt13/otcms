package fu.fa26.sep490.g58.otcms_be.auth.integration;

import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.UUID;

/** Development boundary used when real email delivery is disabled. */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailDelivery implements EmailDeliveryService {
    @Override
    public void sendVerificationCode(String normalizedEmail, String oneTimeCode, UUID accountId) {
        // Deliberately do not log OTP or PII. T009 supplies the real adapter.
    }
}
