package fu.fa26.sep490.g58.otcms_be.notification.model;

import java.util.UUID;

/** Internal publisher input; never accepted from a /me HTTP request. */
public record NotificationMessage(String type, String title, String content, NotificationRelatedKind relatedKind, UUID relatedId) { }
