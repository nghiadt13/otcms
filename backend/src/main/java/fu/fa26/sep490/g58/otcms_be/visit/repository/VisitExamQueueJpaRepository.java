package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitExamQueueJpaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitExamQueueJpaRepository extends JpaRepository<VisitExamQueueJpaEntity, UUID> {
    /**
     * Native read columns, in this exact order (read by ExamQueueItemMapper):
     * 0 visit_id, 1 queue_number, 2 exam_status, 3 source, 4 originating_appointment_id,
     * 5 patient_profile_id, 6 doctor_profile_id, 7 room_id, 8 room display_label, 9 service_id,
     * 10 created_at, 11 queued_at, 12 started_at, 13 finished_at.
     */
    String ROW_COLUMNS = """
            select q.visit_id, q.queue_number, q.exam_status, v.source, v.originating_appointment_id,
                   v.patient_profile_id, q.doctor_profile_id, q.room_id, r.display_label, q.service_id,
                   q.created_at, q.queued_at, q.started_at, q.finished_at
            from visit_exam_queue q
            join visits v on v.id = q.visit_id
            left join rooms r on r.id = q.room_id
            """;

    @Query(value = ROW_COLUMNS + "where q.clinic_date = :clinicDate", nativeQuery = true)
    List<Object[]> findRowsForClinicDate(@Param("clinicDate") LocalDate clinicDate);

    /** Doctor's day: today's rows, plus unfinished IN_EXAMINATION/AWAITING_CONCLUSION rows from earlier days. */
    @Query(value = ROW_COLUMNS + """
            where q.doctor_profile_id = :doctorProfileId
              and q.exam_status not in ('PENDING_PAYMENT', 'CANCELLED')
              and (q.clinic_date = :today
                   or (q.clinic_date < :today and q.exam_status in ('IN_EXAMINATION', 'AWAITING_CONCLUSION')))
            """, nativeQuery = true)
    List<Object[]> findRowsForDoctorDay(@Param("doctorProfileId") UUID doctorProfileId,
            @Param("today") LocalDate today);

    @Query(value = ROW_COLUMNS + "where q.visit_id = :visitId", nativeQuery = true)
    List<Object[]> findRowByVisitId(@Param("visitId") UUID visitId);

    @Query("select coalesce(max(e.queueNumber), 0) from VisitExamQueueJpaEntity e "
            + "where e.doctorProfileId = :doctorProfileId and e.clinicDate = :clinicDate")
    Integer findMaxQueueNumber(@Param("doctorProfileId") UUID doctorProfileId,
            @Param("clinicDate") LocalDate clinicDate);

    long countByDoctorProfileIdAndClinicDateAndExamStatus(UUID doctorProfileId, LocalDate clinicDate,
            VisitExamStatus examStatus);

    boolean existsByDoctorProfileIdAndExamStatusAndVisitIdNot(UUID doctorProfileId, VisitExamStatus examStatus,
            UUID visitId);
}
