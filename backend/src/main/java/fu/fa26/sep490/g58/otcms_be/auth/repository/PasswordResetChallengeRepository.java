package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PasswordResetChallengeJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallengeJpaEntity, UUID> {
    @Query("select c.account.id from PasswordResetChallengeJpaEntity c where c.tokenHash = :hash")
    Optional<UUID> findAccountIdByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update PasswordResetChallengeJpaEntity c set c.supersededAt = :now where c.account.id = :accountId "
            + "and c.consumedAt is null and c.supersededAt is null")
    int supersedeCurrent(@Param("accountId") UUID accountId, @Param("now") Instant now);

    Optional<PasswordResetChallengeJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetChallengeJpaEntity c where c.tokenHash = :tokenHash")
    Optional<PasswordResetChallengeJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetChallengeJpaEntity c where c.account.id = :accountId and c.consumedAt is null and c.supersededAt is null")
    Optional<PasswordResetChallengeJpaEntity> findCurrentByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Query("select c from PasswordResetChallengeJpaEntity c where c.consumedAt < :cutoff or c.expiresAt < :cutoff")
    Page<PasswordResetChallengeJpaEntity> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);
}
