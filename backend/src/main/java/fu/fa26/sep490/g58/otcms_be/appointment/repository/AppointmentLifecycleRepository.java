package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CancellableAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentLifecycleRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, visit_id, source_visit_id, created_at
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findLifecycleRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set status = :target, updated_at = :occurred
            where id = :id and status = :expected and visit_id = :visit
            """, nativeQuery = true)
    int updateLifecycleStatus(@Param("id") UUID id, @Param("expected") String expected,
            @Param("target") String target, @Param("visit") UUID visit, @Param("occurred") Timestamp occurred);

    @Modifying
    @Query(value = """
            insert into appointment_audits (id, appointment_id, actor_account_id, actor_type, action,
                old_status, new_status, old_doctor_profile_id, new_doctor_profile_id,
                old_start_time, new_start_time, old_end_time, new_end_time, reason, linked_visit_id,
                source_visit_id, occurred_at, correlation_id)
            values (:auditId, :id, :actor, :actorType, :action, :oldStatus, :newStatus,
                :doctor, :doctor, :oldStart, :newStart, :oldEnd, :newEnd, cast(:reason as varchar), :visit,
                :sourceVisit, :occurred, :correlation)
            """, nativeQuery = true)
    int insertLifecycleAudit(@Param("auditId") UUID auditId, @Param("id") UUID id,
            @Param("actor") UUID actor, @Param("actorType") String actorType, @Param("action") String action,
            @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus,
            @Param("doctor") UUID doctor, @Param("oldStart") Timestamp oldStart,
            @Param("newStart") Timestamp newStart, @Param("oldEnd") Timestamp oldEnd,
            @Param("newEnd") Timestamp newEnd, @Param("reason") String reason, @Param("visit") UUID visit,
            @Param("sourceVisit") UUID sourceVisit, @Param("occurred") Timestamp occurred,
            @Param("correlation") String correlation);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findLifecycleResult(@Param("id") UUID id);

    default Optional<CancellableAppointment> findForUpdate(UUID id) {
        return findLifecycleRowsForUpdate(id).stream().findFirst().map(row -> new CancellableAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]), (String) row[4],
                (String) row[5], (String) row[6], AppointmentRowMapper.instant(row[7]),
                AppointmentRowMapper.instant(row[8]), AppointmentRowMapper.integer(row[9]),
                AppointmentRowMapper.integer(row[10]), AppointmentRowMapper.uuid(row[11]),
                AppointmentRowMapper.uuid(row[12]), AppointmentRowMapper.instant(row[13])));
    }

    default CreatedAppointment transition(CancellableAppointment current, UUID actor, String actorType,
            String target, Instant occurredAt, String correlationId) {
        return transition(current, actor, actorType, target, null, occurredAt, correlationId);
    }

    default CreatedAppointment transition(CancellableAppointment current, UUID actor, String actorType,
            String target, String reason, Instant occurredAt, String correlationId) {
        if (updateLifecycleStatus(current.id(), current.status(), target, current.visitId(),
                Timestamp.from(occurredAt)) != 1) throw new IllegalStateException("Appointment status changed inside guarded transaction");
        insertLifecycleAudit(UUID.randomUUID(), current.id(), actor, actorType,
                "CANCELLED".equals(target) ? "CANCELLED" : "STATUS_CHANGED", current.status(), target,
                current.doctorProfileId(), Timestamp.from(current.startTime()), Timestamp.from(current.startTime()),
                Timestamp.from(current.endTime()), Timestamp.from(current.endTime()), reason, current.visitId(),
                current.sourceVisitId(), Timestamp.from(occurredAt), correlationId);
        return AppointmentRowMapper.createdAppointment(findLifecycleResult(current.id()).getFirst());
    }
}
