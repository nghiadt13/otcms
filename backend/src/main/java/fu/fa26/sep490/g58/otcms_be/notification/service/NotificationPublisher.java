package fu.fa26.sep490.g58.otcms_be.notification.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import java.util.UUID;

public interface NotificationPublisher {
    void publish(UUID recipientAccountId, NotificationMessage message);
    void publishToPatient(UUID patientProfileId, NotificationMessage message);
    void publishToRole(AccountRole role, NotificationMessage message);
}
