package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareLinkOtpChallengeJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CareLinkOtpChallengeRepository extends JpaRepository<CareLinkOtpChallengeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CareLinkOtpChallengeJpaEntity c where c.linkId = :linkId and c.consumedAt is null and c.supersededAt is null")
    Optional<CareLinkOtpChallengeJpaEntity> findCurrentForUpdate(@Param("linkId") UUID linkId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CareLinkOtpChallengeJpaEntity c where c.id = :id")
    Optional<CareLinkOtpChallengeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CareLinkOtpChallengeJpaEntity c set c.otpHash = :hash "
            + "where c.id = :id and c.linkId = :linkId "
            + "and c.consumedAt is null and c.supersededAt is null "
            + "and c.expiresAt > :now and c.failedAttempts < :maxAttempts")
    int activateIfCurrent(@Param("id") UUID id, @Param("linkId") UUID linkId,
            @Param("hash") String hash, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    long countByRecipientAccountIdAndCreatedAtAfter(UUID recipientAccountId, Instant since);
    long countByLinkIdAndCreatedAtAfter(UUID linkId, Instant since);
}
