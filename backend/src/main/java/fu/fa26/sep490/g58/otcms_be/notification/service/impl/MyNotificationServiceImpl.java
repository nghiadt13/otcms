package fu.fa26.sep490.g58.otcms_be.notification.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.notification.exception.NotificationNotFoundException;
import fu.fa26.sep490.g58.otcms_be.notification.model.dto.NotificationDtos.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.entity.NotificationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.notification.repository.NotificationRepository;
import fu.fa26.sep490.g58.otcms_be.notification.service.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class MyNotificationServiceImpl implements MyNotificationService {
    private final NotificationRepository notifications;
    private final List<NotificationRelatedRecordResolver> resolvers;
    private final Clock clock;
    public MyNotificationServiceImpl(NotificationRepository notifications, List<NotificationRelatedRecordResolver> resolvers, Clock clock) {
        this.notifications = notifications; this.resolvers = List.copyOf(resolvers); this.clock = clock;
    }
    public NotificationPage list(AuthenticatedAccount actor, boolean unread, int page, int size) {
        int normalizedSize = Math.max(1, Math.min(100, size));
        var result = find(actor.accountId(), unread, Math.max(0, page), normalizedSize);
        if (result.getTotalElements() == 0 && result.getNumber() > 0)
            result = new PageImpl<>(List.of(), PageRequest.of(0, normalizedSize), 0);
        else if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages())
            result = find(actor.accountId(), unread, result.getTotalPages() - 1, normalizedSize);
        return new NotificationPage(result.getContent().stream().map(n -> item(actor, n)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), Math.max(1, result.getTotalPages()));
    }
    private Page<NotificationJpaEntity> find(UUID account, boolean unread, int page, int size) {
        var request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return unread ? notifications.findByRecipientAccountIdAndReadAtIsNull(account, request)
                : notifications.findByRecipientAccountId(account, request);
    }
    public UnreadCount unreadCount(AuthenticatedAccount actor) {
        return new UnreadCount(notifications.countByRecipientAccountIdAndReadAtIsNull(actor.accountId()));
    }
    @Transactional
    public Item markRead(AuthenticatedAccount actor, UUID id) {
        var notification = notifications.findOwnedForUpdate(id, actor.accountId()).orElseThrow(NotificationNotFoundException::new);
        if (notification.getReadAt() == null) {
            notification.markRead(clock.instant()); notifications.saveAndFlush(notification);
        }
        return item(actor, notification);
    }
    @Transactional
    public ReadAllResult readAll(AuthenticatedAccount actor) {
        return new ReadAllResult(notifications.markAllRead(actor.accountId(), clock.instant()));
    }
    private Item item(AuthenticatedAccount actor, NotificationJpaEntity notification) {
        boolean read = notification.getReadAt() != null;
        RelatedRecord related = notification.getRelatedKind() == null ? null : new RelatedRecord(notification.getRelatedKind(),
                notification.getRelatedId(), resolvers.stream().filter(r -> r.kind() == notification.getRelatedKind())
                        .anyMatch(r -> r.available(actor, notification.getRelatedId())));
        return new Item(notification.getId(), notification.getTitle(), notification.getContent(),
                notification.getCreatedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(), read, related,
                read ? List.of() : List.of("markRead"));
    }
}
