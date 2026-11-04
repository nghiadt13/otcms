package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingViewerGrantJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingViewerGrantRepository extends JpaRepository<ImagingViewerGrantJpaEntity, UUID> {
    Optional<ImagingViewerGrantJpaEntity> findByTokenSha256(String tokenSha256);
    List<ImagingViewerGrantJpaEntity> findAllByStudyRefAndActorAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
            String studyRef, UUID actorAccountId, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update ImagingViewerGrantJpaEntity grant set grant.revokedAt = :revokedAt "
            + "where grant.studyRef = :studyRef and grant.actorAccountId = :actorId "
            + "and grant.revokedAt is null and grant.expiresAt > :revokedAt")
    int revokeActive(@Param("studyRef") String studyRef, @Param("actorId") UUID actorId,
            @Param("revokedAt") Instant revokedAt);
}
