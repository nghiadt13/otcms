package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.refreshTokenHash = :refreshTokenHash")
    Optional<AuthSession> findByRefreshTokenHashForUpdate(@Param("refreshTokenHash") String refreshTokenHash);

    List<AuthSession> findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(UUID accountId);
    List<AuthSession> findAllByAccountId(UUID accountId);
    Page<AuthSession> findAllByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId, Pageable pageable);

    @Query("select s from AuthSession s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
    Page<AuthSession> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);
}
