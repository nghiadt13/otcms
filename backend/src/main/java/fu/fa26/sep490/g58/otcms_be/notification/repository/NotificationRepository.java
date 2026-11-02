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
}
