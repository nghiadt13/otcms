package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DependentAddRequestRepository extends JpaRepository<VerificationCodeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VerificationCodeJpaEntity r where r.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.DEPENDENT_ADD and r.id = :id")
    Optional<VerificationCodeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VerificationCodeJpaEntity r set r.supersededAt = :now, r.payload = null "
            + "where r.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.DEPENDENT_ADD and r.accountId = :account and r.consumedAt is null and r.supersededAt is null")
    int supersedeOpenRequests(@Param("account") UUID account, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VerificationCodeJpaEntity r set r.codeHash = :hash "
            + "where r.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.DEPENDENT_ADD and r.id = :id and r.consumedAt is null and r.supersededAt is null "
            + "and r.expiresAt > :now and r.failedAttempts < :maxAttempts")
    int activateIfCurrent(@Param("id") UUID id, @Param("hash") String hash,
            @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Query("select count(r) from VerificationCodeJpaEntity r where r.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.DEPENDENT_ADD and r.accountId = :accountId and r.createdAt > :since")
    long countRequestsSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    /**
     * Drops the dependent details (name, national ID...) kept for resend: every earlier request of {@code account}
     * (about to be replaced by a new one) plus any account's request that expired before {@code cutoff}. Other
     * accounts' recently expired requests are kept so their owners can still resend.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VerificationCodeJpaEntity r set r.payload = null "
            + "where r.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.DEPENDENT_ADD and r.payload is not null "
            + "and (r.accountId = :account or r.expiresAt <= :cutoff)")
    int clearStalePayloads(@Param("account") UUID account, @Param("cutoff") Instant cutoff);
}
