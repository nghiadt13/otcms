package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Exam-queue rows of {@code queue_entries}. Every query filters {@code stage = 'EXAM'}: the reception queue shares
 * the table and the WAITING status, so a query without the stage would mix the two queues. Do not use the inherited
 * {@code findById}/{@code existsById} with a visit id: the key is the row id; use the {@code ...ExamByVisitId} methods.
 */
public interface VisitExamQueueJpaRepository extends JpaRepository<QueueEntryJpaEntity, UUID> {
    /**
     * Native read columns, in this exact order (read by ExamQueueItemMapper):
     * 0 visit_id, 1 queue_number, 2 exam_status, 3 source, 4 originating_appointment_id,
     * 5 patient_profile_id, 6 doctor_profile_id, 7 room_id, 8 room display_label, 9 service_id,
     * 10 created_at, 11 queued_at, 12 started_at, 13 finished_at.
     * Callers append conditions with {@code and ...} (the stage filter is already here).
     */
    String ROW_COLUMNS = """
            select q.visit_id, q.queue_number, q.status as exam_status, v.source, v.originating_appointment_id,
                   v.patient_profile_id, q.doctor_profile_id, q.room_id, r.display_label, q.service_id,
                   q.entered_at as created_at, q.queued_at, q.started_at, q.finished_at
            from queue_entries q
            join visits v on v.id = q.visit_id
            left join rooms r on r.id = q.room_id
            where q.stage = 'EXAM'
            """;

    @Query(value = ROW_COLUMNS + "and q.clinic_date = :clinicDate", nativeQuery = true)
    List<Object[]> findRowsForClinicDate(@Param("clinicDate") LocalDate clinicDate);

    /** Doctor's day: today's rows, plus unfinished IN_EXAMINATION/AWAITING_CONCLUSION rows from earlier days. */
    @Query(value = ROW_COLUMNS + """
            and q.doctor_profile_id = :doctorProfileId
              and q.status not in ('PENDING_PAYMENT', 'CANCELLED')
              and (q.clinic_date = :today
                   or (q.clinic_date < :today and q.status in ('IN_EXAMINATION', 'AWAITING_CONCLUSION')))
            """, nativeQuery = true)
    List<Object[]> findRowsForDoctorDay(@Param("doctorProfileId") UUID doctorProfileId,
            @Param("today") LocalDate today);

    @Query(value = ROW_COLUMNS + "and q.visit_id = :visitId", nativeQuery = true)
    List<Object[]> findRowByVisitId(@Param("visitId") UUID visitId);

    /** The visit's exam row (at most one: uq_queue_entries_exam_visit). */
    @Query("select e from QueueEntryJpaEntity e where e.stage = 'EXAM' and e.visitId = :visitId")
    Optional<QueueEntryJpaEntity> findExamByVisitId(@Param("visitId") UUID visitId);

    @Query("select count(e) > 0 from QueueEntryJpaEntity e where e.stage = 'EXAM' and e.visitId = :visitId")
    boolean existsExamByVisitId(@Param("visitId") UUID visitId);

    @Query("select coalesce(max(e.queueNumber), 0) from QueueEntryJpaEntity e "
            + "where e.stage = 'EXAM' and e.doctorProfileId = :doctorProfileId and e.clinicDate = :clinicDate")
    Integer findMaxQueueNumber(@Param("doctorProfileId") UUID doctorProfileId,
            @Param("clinicDate") LocalDate clinicDate);

    /** {@code status} is a VisitExamStatus name. */
    @Query("select count(e) from QueueEntryJpaEntity e where e.stage = 'EXAM' "
            + "and e.doctorProfileId = :doctorProfileId and e.clinicDate = :clinicDate and e.status = :status")
    long countExamRows(@Param("doctorProfileId") UUID doctorProfileId, @Param("clinicDate") LocalDate clinicDate,
            @Param("status") String status);

    /** {@code status} is a VisitExamStatus name. */
    @Query("select count(e) > 0 from QueueEntryJpaEntity e where e.stage = 'EXAM' "
            + "and e.doctorProfileId = :doctorProfileId and e.status = :status and e.visitId <> :visitId")
    boolean existsOtherExamRow(@Param("doctorProfileId") UUID doctorProfileId, @Param("status") String status,
            @Param("visitId") UUID visitId);
}
