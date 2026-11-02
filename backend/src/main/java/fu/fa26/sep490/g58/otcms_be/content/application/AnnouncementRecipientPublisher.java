package fu.fa26.sep490.g58.otcms_be.content.application;
import java.util.UUID;
/** Adapter to KAN-14's NotificationPublisher, provided only after that dependency is merged. */
public interface AnnouncementRecipientPublisher {
    void publishToRole(String role, String title, String body, UUID announcementId);
}
