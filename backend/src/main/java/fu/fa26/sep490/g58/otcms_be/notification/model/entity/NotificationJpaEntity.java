package fu.fa26.sep490.g58.otcms_be.notification.model.entity;

import fu.fa26.sep490.g58.otcms_be.notification.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "notifications")
public class NotificationJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID recipientAccountId;
    @Column(nullable = false, length = 48) private String type;
    @Column(nullable = false, length = 240) private String title;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Enumerated(EnumType.STRING) @Column(length = 24) private NotificationRelatedKind relatedKind;
    private UUID relatedId;
    @Column(nullable = false) private Instant createdAt;
    private Instant readAt;
    protected NotificationJpaEntity() { }
    public static NotificationJpaEntity create(UUID recipient, NotificationMessage message, Instant now) {
        var item = new NotificationJpaEntity();
        item.id = UUID.randomUUID(); item.recipientAccountId = recipient; item.type = message.type();
        item.title = message.title(); item.content = message.content(); item.relatedKind = message.relatedKind();
        item.relatedId = message.relatedId(); item.createdAt = now;
        return item;
    }
    public void markRead(Instant now) { if (readAt == null) readAt = now; }
    public UUID getId() { return id; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public NotificationRelatedKind getRelatedKind() { return relatedKind; }
    public UUID getRelatedId() { return relatedId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
