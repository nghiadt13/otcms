package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.EmailVerificationChallengeJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallengeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationChallengeJpaEntity c where c.account.id = :accountId and c.consumedAt is null and c.supersededAt is null")
    Optional<EmailVerificationChallengeJpaEntity> findCurrentByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationChallengeJpaEntity c where c.account.id = :accountId and c.consumedAt is null and c.supersededAt is null")
    Optional<EmailVerificationChallengeJpaEntity> findCurrentForUpdate(@Param("accountId") UUID accountId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update EmailVerificationChallengeJpaEntity c set c.supersededAt = :now where c.account.id = :accountId and c.consumedAt is null and c.supersededAt is null")
    int supersedeCurrent(@Param("accountId") UUID accountId, @Param("now") Instant now);

    long countByAccountIdAndCreatedAtAfter(UUID accountId, Instant since);

    @Query("select c from EmailVerificationChallengeJpaEntity c where c.consumedAt < :cutoff or c.expiresAt < :cutoff")
    Page<EmailVerificationChallengeJpaEntity> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);
}
