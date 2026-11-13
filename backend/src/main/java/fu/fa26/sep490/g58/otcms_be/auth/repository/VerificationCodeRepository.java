package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Account-scoped codes (EMAIL_VERIFY, EMAIL_CHANGE, PASSWORD_RESET, DEPENDENT_ADD). At most one open code per
 * account and purpose is enforced by uk_verification_codes_account_current: call supersedeCurrent before
 * inserting a new code in the same transaction. Every method is scoped by purpose so flows stay separate.
 */
public interface VerificationCodeRepository extends JpaRepository<VerificationCodeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationCodeJpaEntity c where c.accountId = :accountId and c.purpose = :purpose "
            + "and c.consumedAt is null and c.supersededAt is null")
    Optional<VerificationCodeJpaEntity> findCurrentForUpdate(@Param("accountId") UUID accountId,
            @Param("purpose") VerificationPurpose purpose);

    @Modifying
    @Query("update VerificationCodeJpaEntity c set c.supersededAt = :now where c.accountId = :accountId "
            + "and c.purpose = :purpose and c.consumedAt is null and c.supersededAt is null")
    int supersedeCurrent(@Param("accountId") UUID accountId, @Param("purpose") VerificationPurpose purpose,
            @Param("now") Instant now);

    long countByAccountIdAndPurposeAndCreatedAtAfter(UUID accountId, VerificationPurpose purpose, Instant since);

    @Query("select c.accountId from VerificationCodeJpaEntity c where c.codeHash = :hash and c.purpose = :purpose")
    Optional<UUID> findAccountIdByCodeHash(@Param("hash") String hash, @Param("purpose") VerificationPurpose purpose);

    @Query("select c from VerificationCodeJpaEntity c where c.codeHash = :hash and c.purpose = :purpose")
    Optional<VerificationCodeJpaEntity> findByCodeHash(@Param("hash") String hash,
            @Param("purpose") VerificationPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationCodeJpaEntity c where c.codeHash = :hash and c.purpose = :purpose")
    Optional<VerificationCodeJpaEntity> findByCodeHashForUpdate(@Param("hash") String hash,
            @Param("purpose") VerificationPurpose purpose);

    @Query("select c from VerificationCodeJpaEntity c where c.purpose = :purpose "
            + "and (c.consumedAt < :cutoff or c.expiresAt < :cutoff)")
    Page<VerificationCodeJpaEntity> findPurgeCandidates(@Param("purpose") VerificationPurpose purpose,
            @Param("cutoff") Instant cutoff, Pageable pageable);
}
