package fu.fa26.sep490.g58.otcms_be.notification.repository;

import fu.fa26.sep490.g58.otcms_be.notification.model.entity.NotificationJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    Page<NotificationJpaEntity> findByRecipientAccountId(UUID recipient, Pageable page);
    Page<NotificationJpaEntity> findByRecipientAccountIdAndReadAtIsNull(UUID recipient, Pageable page);
    long countByRecipientAccountIdAndReadAtIsNull(UUID recipient);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationJpaEntity n where n.id = :id and n.recipientAccountId = :recipient")
    Optional<NotificationJpaEntity> findOwnedForUpdate(UUID id, UUID recipient);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationJpaEntity n set n.readAt = :now where n.recipientAccountId = :recipient and n.readAt is null")
    int markAllRead(UUID recipient, Instant now);

    /**
     * NF-09: inserts a notification unless one with the same dedupe key exists (partial unique index, V127).
     * Runs in its own transaction so one failing reminder never rolls back the others.
     * @return 1 when inserted, 0 when the key already existed
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notifications (id, recipient_account_id, type, title, content, related_kind, related_id, created_at, dedupe_key)
            VALUES (:id, :recipient, :type, :title, :content, :relatedKind, :relatedId, :createdAt, :dedupeKey)
            ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
            """)
    int insertIfAbsent(UUID id, UUID recipient, String type, String title, String content, String relatedKind,
            UUID relatedId, Instant createdAt, String dedupeKey);
}
