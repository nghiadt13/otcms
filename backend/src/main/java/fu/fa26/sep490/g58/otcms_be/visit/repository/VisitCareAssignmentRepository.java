package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitCareAssignmentRepository extends JpaRepository<VisitCareAssignmentJpaEntity, UUID> {
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
