package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.patient.model.entity.DependentAddRequestJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DependentAddRequestRepository extends JpaRepository<DependentAddRequestJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DependentAddRequestJpaEntity r where r.id = :id")
    Optional<DependentAddRequestJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DependentAddRequestJpaEntity r set r.supersededAt = :now "
            + "where r.requesterAccountId = :account and r.consumedAt is null and r.supersededAt is null")
    int supersedeOpenRequests(@Param("account") UUID account, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DependentAddRequestJpaEntity r set r.otpHash = :hash "
            + "where r.id = :id and r.consumedAt is null and r.supersededAt is null "
            + "and r.expiresAt > :now and r.failedAttempts < :maxAttempts")
    int activateIfCurrent(@Param("id") UUID id, @Param("hash") String hash,
            @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    long countByRequesterAccountIdAndCreatedAtAfter(UUID requesterAccountId, Instant since);
}
