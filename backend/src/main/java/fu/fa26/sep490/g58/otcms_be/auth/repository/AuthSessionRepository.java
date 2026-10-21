package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
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

public interface AuthSessionRepository extends JpaRepository<AuthSessionJpaEntity, UUID> {
    Optional<AuthSessionJpaEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionJpaEntity s where s.refreshTokenHash = :refreshTokenHash")
    Optional<AuthSessionJpaEntity> findByRefreshTokenHashForUpdate(@Param("refreshTokenHash") String refreshTokenHash);

    List<AuthSessionJpaEntity> findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(UUID accountId);
    List<AuthSessionJpaEntity> findAllByAccountId(UUID accountId);
    Page<AuthSessionJpaEntity> findAllByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId, Pageable pageable);

    @Query("select s from AuthSessionJpaEntity s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
    Page<AuthSessionJpaEntity> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);
}
