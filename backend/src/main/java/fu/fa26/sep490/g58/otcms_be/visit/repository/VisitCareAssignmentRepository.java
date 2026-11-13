package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitCareAssignmentRepository extends JpaRepository<VisitCareAssignmentJpaEntity, UUID> {
    Optional<VisitCareAssignmentJpaEntity> findFirstByVisitIdAndDoctorProfileIdAndStatusInOrderByAssignedAtDescIdDesc(
            UUID visitId, UUID doctorProfileId, java.util.Collection<String> statuses);
    @Query("""
            select (count(a) > 0) from VisitCareAssignmentJpaEntity a join ClinicVisitJpaEntity v on v.id = a.visitId
            where a.doctorProfileId = :doctorId and v.patientProfileId = :patientId
                and a.status = 'IN_PROGRESS' and v.status = 'OPEN'
            """)
    boolean hasCurrentDoctorCare(@Param("doctorId") UUID doctorProfileId, @Param("patientId") UUID patientProfileId);

    interface DoctorActiveCareRow {
        UUID getVisitId();
        UUID getPatientProfileId();
        String getVisitSource();
        UUID getLinkedAppointmentId();
        UUID getAssignmentId();
        String getStepCode();
        Instant getStartedAt();
        Instant getExpectedEnd();
        /** VisitExamStatus name, null without an exam-queue row (queue_entries.status is a plain string). */
        String getExamStatus();
        Integer getQueueNumber();
    }

    /** Left join: a visit without an exam-queue row (legacy data) still lists with null status and number. */
    @Query("""
            select v.id as visitId, v.patientProfileId as patientProfileId, v.source as visitSource,
                   v.originatingAppointmentId as linkedAppointmentId, a.id as assignmentId,
                   a.stepCode as stepCode, a.startsAt as startedAt, a.expectedEnd as expectedEnd,
                   q.status as examStatus, q.queueNumber as queueNumber
            from VisitCareAssignmentJpaEntity a join ClinicVisitJpaEntity v on v.id = a.visitId
                left join QueueEntryJpaEntity q on q.visitId = v.id and q.stage = 'EXAM'
            where a.doctorProfileId = :doctorProfileId and a.status = 'IN_PROGRESS' and v.status = 'OPEN'
            order by a.startsAt asc, a.id asc
            """)
    List<DoctorActiveCareRow> findDoctorActiveCare(@Param("doctorProfileId") UUID doctorProfileId);

    java.util.List<VisitCareAssignmentJpaEntity> findByVisitIdInAndStatusOrderByAssignedAtDescIdDesc(
            java.util.Collection<UUID> visitIds, String status);

    Optional<VisitCareAssignmentJpaEntity> findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(
            UUID visitId, String status);

    @Query(value = """
            select count(*) from visit_assignments where doctor_profile_id = :doctorId
              and status in ('RESERVED', 'IN_PROGRESS')
              and starts_at < :toExclusive and ends_at > :fromInclusive
            """, nativeQuery = true)
    long countActiveCareOverlapping(@Param("doctorId") UUID doctorId,
            @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select count(*) from visit_assignments where doctor_profile_id = :doctorId
              and status in ('RESERVED', 'IN_PROGRESS')
            """, nativeQuery = true)
    long countActiveCare(@Param("doctorId") UUID doctorId);

    @Query(value = """
            select starts_at, ends_at from visit_assignments
            where doctor_profile_id = :doctorId and status in ('RESERVED', 'IN_PROGRESS')
            order by starts_at, id
            """, nativeQuery = true)
    List<Object[]> findActiveCareIntervals(@Param("doctorId") UUID doctorId);

    @Query(value = """
            select c.id, c.doctor_profile_id, v.patient_profile_id, v.originating_appointment_id,
                   c.starts_at, c.ends_at
            from visit_assignments c join visits v on v.id = c.visit_id
            where c.doctor_profile_id in (:doctorIds)
              and c.status in ('RESERVED', 'IN_PROGRESS')
              and c.starts_at < :toExclusive and c.ends_at > :fromInclusive
            order by c.doctor_profile_id, c.starts_at, c.id
            """, nativeQuery = true)
    List<Object[]> findActiveCareReservationRows(@Param("doctorIds") List<UUID> doctorIds,
            @Param("fromInclusive") Timestamp fromInclusive, @Param("toExclusive") Timestamp toExclusive);

    @Query(value = """
            select id, patient_profile_id, source, originating_appointment_id, status
            from visits where id = :visitId for update
            """, nativeQuery = true)
    List<Object[]> findVisitForUpdate(@Param("visitId") UUID visitId);

    @Query(value = """
            select id, visit_id, doctor_profile_id, starts_at, ends_at, status, step_code,
                   expected_duration_minutes, actual_ended_at
            from visit_assignments
            where id = :assignmentId and visit_id = :visitId
            for update
            """, nativeQuery = true)
    List<Object[]> findAssignmentForUpdate(@Param("assignmentId") UUID assignmentId,
            @Param("visitId") UUID visitId);

    @Query(value = """
            select count(*) from visit_assignments
            where doctor_profile_id = :doctorId
              and status in ('RESERVED', 'IN_PROGRESS')
              and starts_at < :toExclusive and ends_at > :fromInclusive
              and (cast(:excludedAssignmentId as uuid) is null or id <> cast(:excludedAssignmentId as uuid))
            """, nativeQuery = true)
    long countDoctorConflicts(@Param("doctorId") UUID doctorId,
            @Param("fromInclusive") Timestamp fromInclusive,
            @Param("toExclusive") Timestamp toExclusive,
            @Param("excludedAssignmentId") UUID excludedAssignmentId);

    @Query(value = """
            select count(*) from visit_assignments
            where visit_id = :visitId and status in ('RESERVED', 'IN_PROGRESS')
              and (cast(:excludedAssignmentId as uuid) is null or id <> cast(:excludedAssignmentId as uuid))
            """, nativeQuery = true)
    long countVisitActiveCare(@Param("visitId") UUID visitId,
            @Param("excludedAssignmentId") UUID excludedAssignmentId);

    @Modifying
    @Query(value = """
            update visit_assignments
            set ends_at = :newEnd, expected_duration_minutes = :newDuration
            where id = :assignmentId and visit_id = :visitId and status = 'IN_PROGRESS'
            """, nativeQuery = true)
    int extend(@Param("assignmentId") UUID assignmentId, @Param("visitId") UUID visitId,
            @Param("newEnd") Timestamp newEnd, @Param("newDuration") int newDuration);

    @Modifying
    @Query(value = """
            update visit_assignments
            set status = 'COMPLETED', actual_ended_at = :actualEnd
            where id = :assignmentId and visit_id = :visitId and doctor_profile_id = :doctorId
              and status = 'IN_PROGRESS'
            """, nativeQuery = true)
    int complete(@Param("assignmentId") UUID assignmentId, @Param("visitId") UUID visitId,
            @Param("doctorId") UUID doctorId, @Param("actualEnd") Timestamp actualEnd);
}
