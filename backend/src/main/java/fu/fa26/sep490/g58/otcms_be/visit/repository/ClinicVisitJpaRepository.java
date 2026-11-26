package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Propagation;

public interface ClinicVisitJpaRepository extends JpaRepository<ClinicVisitJpaEntity, UUID> {
    boolean existsByPatientProfileIdAndStatus(UUID patientProfileId, String status);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where (:patientId is null or v.patientProfileId = :patientId)
              and (:status is null or v.status = :status)
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Slice<ClinicVisitJpaEntity> findReceptionVisits(
            @Param("patientId") UUID patientId, @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.patientProfileId in :patientIds
                and (v.status in ('OPEN', 'INCOMPLETE') or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Page<ClinicVisitJpaEntity> findPatientPortalHistory(
            @Param("patientIds") java.util.Collection<UUID> patientIds,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.id = :visitId and v.patientProfileId in :patientIds
                and (v.status in ('OPEN', 'INCOMPLETE') or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
            """)
    Optional<ClinicVisitJpaEntity> findPatientPortalVisit(
            @Param("visitId") UUID visitId, @Param("patientIds") java.util.Collection<UUID> patientIds);

    @Query("""
            select v.id from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId
                and (v.status in ('OPEN', 'INCOMPLETE') or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
                and (v.arrivedAt < :arrivedAt or (v.arrivedAt = :arrivedAt and v.id < :visitId))
            order by v.arrivedAt desc, v.id desc
            """)
    java.util.List<UUID> findPatientPortalPreviousVisit(
            @Param("patientId") UUID patientId, @Param("arrivedAt") java.time.Instant arrivedAt,
            @Param("visitId") UUID visitId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v.id from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId
                and (v.status in ('OPEN', 'INCOMPLETE') or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
                and (v.arrivedAt > :arrivedAt or (v.arrivedAt = :arrivedAt and v.id > :visitId))
            order by v.arrivedAt asc, v.id asc
            """)
    java.util.List<UUID> findPatientPortalNextVisit(
            @Param("patientId") UUID patientId, @Param("arrivedAt") java.time.Instant arrivedAt,
            @Param("visitId") UUID visitId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId and v.id <> :currentId and v.arrivedAt <= :before
                and v.status = 'CLOSED'
                and exists (select a.id from VisitCareAssignmentJpaEntity a where a.visitId = v.id and a.status = 'COMPLETED')
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Page<ClinicVisitJpaEntity> findHistoricalVisits(
            @Param("patientId") UUID patientProfileId, @Param("currentId") UUID currentVisitId,
            @Param("before") java.time.Instant before, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId and v.id <> :currentId and v.arrivedAt <= :before
                and v.status = 'CLOSED'
                and exists (select a.id from VisitCareAssignmentJpaEntity a where a.visitId = v.id and a.status = 'COMPLETED')
                and (:filterByDate = false or (v.arrivedAt >= :fromInclusive and v.arrivedAt < :toExclusive))
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Page<ClinicVisitJpaEntity> findHistoricalVisitsFiltered(
            @Param("patientId") UUID patientProfileId, @Param("currentId") UUID currentVisitId,
            @Param("before") java.time.Instant before, @Param("filterByDate") boolean filterByDate,
            @Param("fromInclusive") java.time.Instant fromInclusive,
            @Param("toExclusive") java.time.Instant toExclusive,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Lượt của các hồ sơ trong phạm vi (BR-19) đang mở hoặc check-in từ {@code since}; bỏ lượt còn chờ thu phí.
     * Cột: id, patient_profile_id, checked_in_at, status, trạng thái khám gần nhất, số thứ tự và tên phòng của
     * hàng đang chờ khám hoặc dịch vụ (null khi không ở hàng).
     */
    @Query(value = """
            select v.id, v.patient_profile_id, v.checked_in_at, v.status, exam.status, active.queue_number,
                   active.room_label
            from visits v
            left join lateral (
                select q.status from queue_entries q
                where q.visit_id = v.id and q.stage = 'EXAM'
                order by q.entered_at desc, q.id desc limit 1
            ) exam on true
            left join lateral (
                select q.queue_number, room.display_label as room_label
                from queue_entries q left join rooms room on room.id = q.room_id
                where q.visit_id = v.id and q.stage in ('EXAM', 'SERVICE')
                  and q.status in ('WAITING', 'CALLED', 'IN_EXAMINATION')
                order by q.entered_at desc, q.id desc limit 1
            ) active on true
            where v.patient_profile_id in (:profileIds)
              and (v.status = 'OPEN' or v.checked_in_at >= :since)
              and coalesce(exam.status, '') <> 'PENDING_PAYMENT'
            order by v.checked_in_at desc, v.id desc
            """, nativeQuery = true)
    java.util.List<Object[]> findMyVisitRows(
            @Param("profileIds") java.util.Collection<UUID> profileIds, @Param("since") java.sql.Timestamp since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from ClinicVisitJpaEntity visit where visit.id = :visitId")
    Optional<ClinicVisitJpaEntity> findByIdForUpdate(@Param("visitId") UUID visitId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ClinicVisitJpaEntity v set v.imagingResultReadyAt = coalesce(v.imagingResultReadyAt, :at) "
            + "where v.id = :visitId and v.patientProfileId = :patientId")
    int markImagingResultReady(@Param("visitId") UUID visitId, @Param("patientId") UUID patientId,
            @Param("at") java.time.Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ClinicVisitJpaEntity v set v.findingsReleasedAt = coalesce(v.findingsReleasedAt, :at) "
            + "where v.id = :visitId and v.patientProfileId = :patientId")
    int markFindingsReleased(@Param("visitId") UUID visitId, @Param("patientId") UUID patientId,
            @Param("at") java.time.Instant at);
}
