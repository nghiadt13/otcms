package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.entity.PasswordResetChallenge;
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

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, UUID> {
    Optional<PasswordResetChallenge> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetChallenge c where c.tokenHash = :tokenHash")
    Optional<PasswordResetChallenge> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetChallenge c where c.account.id = :accountId and c.consumedAt is null and c.supersededAt is null")
    Optional<PasswordResetChallenge> findCurrentByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Query("select c from PasswordResetChallenge c where c.consumedAt < :cutoff or c.expiresAt < :cutoff")
    Page<PasswordResetChallenge> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);
}
