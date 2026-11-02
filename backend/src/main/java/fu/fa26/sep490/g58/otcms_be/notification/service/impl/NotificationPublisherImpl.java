package fu.fa26.sep490.g58.otcms_be.notification.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.NotificationRecipientDirectory;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.notification.model.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.entity.NotificationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.notification.repository.NotificationRepository;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Never opens an independent transaction: source write, history and inbox inserts are atomic. */
@Service @Transactional(propagation = Propagation.MANDATORY)
public class NotificationPublisherImpl implements NotificationPublisher {
    private final NotificationRepository notifications;
    private final NotificationRecipientDirectory recipients;
    private final Clock clock;
    public NotificationPublisherImpl(NotificationRepository notifications, NotificationRecipientDirectory recipients, Clock clock) {
        this.notifications = notifications; this.recipients = recipients; this.clock = clock;
    }
    public void publish(UUID recipient, NotificationMessage message) {
        if (recipients.active(recipient)) insert(recipient, message);
    }
    public void publishToPatient(UUID profileId, NotificationMessage message) {
        recipients.patientAccount(profileId).ifPresent(id -> insert(id, message));
    }
    public void publishToRole(AccountRole role, NotificationMessage message) {
        recipients.activeAccounts(role).forEach(id -> insert(id, message));
    }
    private void insert(UUID recipient, NotificationMessage message) {
        notifications.saveAndFlush(NotificationJpaEntity.create(recipient, message, clock.instant()));
    }
}
