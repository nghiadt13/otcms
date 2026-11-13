package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CareLinkOtpChallengeRepository extends JpaRepository<VerificationCodeJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationCodeJpaEntity c where c.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.CARE_LINK and c.linkId = :linkId and c.consumedAt is null and c.supersededAt is null")
    Optional<VerificationCodeJpaEntity> findCurrentForUpdate(@Param("linkId") UUID linkId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationCodeJpaEntity c where c.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.CARE_LINK and c.id = :id")
    Optional<VerificationCodeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VerificationCodeJpaEntity c set c.codeHash = :hash "
            + "where c.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.CARE_LINK and c.id = :id and c.linkId = :linkId "
            + "and c.consumedAt is null and c.supersededAt is null "
            + "and c.expiresAt > :now and c.failedAttempts < :maxAttempts")
    int activateIfCurrent(@Param("id") UUID id, @Param("linkId") UUID linkId,
            @Param("hash") String hash, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Query("select count(c) from VerificationCodeJpaEntity c where c.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.CARE_LINK and c.accountId = :accountId and c.createdAt > :since")
    long countRecipientSince(@Param("accountId") UUID accountId, @Param("since") Instant since);
    @Query("select count(c) from VerificationCodeJpaEntity c where c.purpose = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose.CARE_LINK and c.linkId = :linkId and c.createdAt > :since")
    long countLinkSince(@Param("linkId") UUID linkId, @Param("since") Instant since);
}
