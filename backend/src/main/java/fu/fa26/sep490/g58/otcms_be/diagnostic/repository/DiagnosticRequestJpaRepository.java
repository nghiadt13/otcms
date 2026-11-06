package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticRequestJpaRepository extends JpaRepository<DiagnosticRequestJpaEntity, UUID> {
    interface ReleasedHistoryRow {
        UUID getPatientId();
        UUID getRequestId();
        UUID getVisitId();
        String getImagingType();
        DiagnosticRequestStatus getStatus();
        String getStudyRef();
        Integer getFindingsVersion();
        String getFindingsText();
        UUID getReportId();
    }

    @Query("""
            select r.patientId as patientId, r.id as requestId, r.visitId as visitId,
                r.imagingType as imagingType, r.status as status, f.studyRef as studyRef,
                f.versionNumber as findingsVersion, f.findingsText as findingsText, p.id as reportId
            from DiagnosticRequestJpaEntity r, DiagnosticFindingsVersionJpaEntity f, DiagnosticReportJpaEntity p
            where r.patientId = :patientId and r.visitId in :visitIds and r.status in :statuses
                and f.requestId = r.id and f.visitId = r.visitId
                and f.state = fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.COMPLETED
                and f.completedAt is not null
                and p.id = f.requiredPdfReportId and p.requestId = r.id and p.visitId = r.visitId
                and p.studyRef = f.studyRef and p.releasedAt is not null
                and p.validationStatus = fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED
                and not exists (select newer.id from DiagnosticFindingsVersionJpaEntity newer
                    where newer.requestId = r.id and newer.versionNumber > f.versionNumber
                    and newer.state = fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.COMPLETED)
            order by r.createdAt desc, r.id desc
            """)
    List<ReleasedHistoryRow> findReleasedPatientHistory(@Param("patientId") UUID patientId,
            @Param("visitIds") Collection<UUID> visitIds, @Param("statuses") Collection<DiagnosticRequestStatus> statuses);

    @Query("""
            select request from DiagnosticRequestJpaEntity request
             where request.currentAttemptId is not null
               and exists (select attempt.id from DiagnosticAttemptJpaEntity attempt
                            where attempt.requestId = request.id
                              and attempt.id = request.currentAttemptId
                              and attempt.studyRef = :studyRef)
            """)
    Optional<DiagnosticRequestJpaEntity> findByCurrentStudyRef(@Param("studyRef") String studyRef);

    // Intentionally global: duplicate live requests, even across Visits, must fail closed.
    List<DiagnosticRequestJpaEntity> findAllByServiceOrderIdInAndStatusNot(
            Collection<UUID> serviceOrderIds, DiagnosticRequestStatus status);

    Optional<DiagnosticRequestJpaEntity> findFirstByServiceOrderIdOrderByCreatedAtDescIdDesc(UUID serviceOrderId);

    // Same transaction as the Clinical order/replay. Partial unique index V097 serializes concurrent registration.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = """
            insert into diagnostic_requests (id, visit_id, patient_id, ordering_doctor_id, service_id,
                imaging_type, body_area, clinical_reason, service_order_id, status)
            values (:id, :visitId, :patientId, :doctorAccountId, :serviceId, :modality, :area, :reason, :orderId, 'PENDING')
            on conflict (service_order_id) where service_order_id is not null and status <> 'CANCELLED' do nothing
            """, nativeQuery = true)
    int insertUnassigned(@Param("id") UUID id, @Param("visitId") UUID visitId, @Param("patientId") UUID patientId,
            @Param("doctorAccountId") UUID doctorAccountId, @Param("serviceId") UUID serviceId,
            @Param("modality") String modality, @Param("area") String area, @Param("reason") String reason,
            @Param("orderId") UUID orderId);

    /** True when a request other than the given status (use CANCELLED) already fulfils the order. */
    boolean existsByServiceOrderIdAndStatusNot(UUID serviceOrderId, DiagnosticRequestStatus status);

    List<DiagnosticRequestJpaEntity> findAllByVisitIdOrderByCreatedAtDescIdDesc(UUID visitId);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdOrderByCreatedAtDescIdDesc(UUID visitId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(
            UUID visitId, UUID patientId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByPatientIdOrderByCreatedAtDescIdDesc(UUID patientId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
            UUID roomId, Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdAndOrderingDoctorIdOrderByCreatedAtDescIdDesc(
            UUID visitId, UUID orderingDoctorId, Pageable pageable);

    List<DiagnosticRequestJpaEntity> findAllByPatientIdOrderByCreatedAtDescIdDesc(UUID patientId);

    List<DiagnosticRequestJpaEntity> findAllByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            DiagnosticRequestStatus status, Instant createdBefore);

    Page<DiagnosticRequestJpaEntity> findAllByStatusInOrderByCreatedAtAscIdAsc(
            Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByStatusInOrderByCreatedAtDescIdDesc(
            Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from DiagnosticRequestJpaEntity request where request.id = :requestId")
    Optional<DiagnosticRequestJpaEntity> findByIdForUpdate(@Param("requestId") UUID requestId);
}
