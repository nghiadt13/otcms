package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.EmailChangeChallengeJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailChangeChallengeRepository extends JpaRepository<EmailChangeChallengeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailChangeChallengeJpaEntity c where c.accountId = :id and c.consumedAt is null and c.supersededAt is null")
    Optional<EmailChangeChallengeJpaEntity> findCurrentForUpdate(@Param("id") UUID accountId);

    @Modifying
    @Query("update EmailChangeChallengeJpaEntity c set c.supersededAt = :now where c.accountId = :id and c.consumedAt is null and c.supersededAt is null")
    int supersedeCurrent(@Param("id") UUID accountId, @Param("now") Instant now);

    long countByAccountIdAndCreatedAtAfter(UUID accountId, Instant since);
}
