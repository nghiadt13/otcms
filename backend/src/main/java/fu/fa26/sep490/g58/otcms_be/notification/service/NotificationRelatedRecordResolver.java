package fu.fa26.sep490.g58.otcms_be.notification.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import java.util.UUID;

/** Implemented by record owners: notification never imports appointment/visit/review internals. */
public interface NotificationRelatedRecordResolver {
    NotificationRelatedKind kind();
    boolean available(AuthenticatedAccount actor, UUID relatedId);
}
