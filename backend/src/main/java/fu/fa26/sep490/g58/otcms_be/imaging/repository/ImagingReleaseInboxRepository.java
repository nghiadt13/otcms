package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingReleaseInboxRepository extends JpaRepository<ImagingReleaseInboxJpaEntity, UUID> {
    Optional<ImagingReleaseInboxJpaEntity> findByEventId(UUID eventId);

    @Modifying
    @Query(value = """
            insert into imaging_release_inbox
                (id, event_id, request_id, visit_id, patient_id, study_ref, findings_version_id,
                 report_id, report_sha256, payload_sha256, outcome, acknowledged_at)
            values (:id, :eventId, :requestId, :visitId, :patientId, :studyRef, :findingsVersionId,
                    :reportId, :reportSha256, :payloadSha256, 'ACCEPTED', :acknowledgedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertAcceptedIfAbsent(@Param("id") UUID id, @Param("eventId") UUID eventId,
            @Param("requestId") UUID requestId, @Param("visitId") UUID visitId,
            @Param("patientId") UUID patientId, @Param("studyRef") String studyRef,
            @Param("findingsVersionId") UUID findingsVersionId, @Param("reportId") UUID reportId,
            @Param("reportSha256") String reportSha256, @Param("payloadSha256") String payloadSha256,
            @Param("acknowledgedAt") Instant acknowledgedAt);
}
