package fu.fa26.sep490.g58.otcms_be.notification.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.notification.model.dto.NotificationDtos.*;
import java.util.UUID;

public interface MyNotificationService {
    NotificationPage list(AuthenticatedAccount actor, boolean unread, int page, int size);
    UnreadCount unreadCount(AuthenticatedAccount actor);
    Item markRead(AuthenticatedAccount actor, UUID id);
    ReadAllResult readAll(AuthenticatedAccount actor);
}
