package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

public interface EmailDeliveryService {
    void sendVerificationCode(String normalizedEmail, String oneTimeCode, UUID accountId);
    default void sendPasswordReset(String normalizedEmail, String resetToken, UUID accountId) { }
}
