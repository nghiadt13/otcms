package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CancellableAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentCancellationRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, visit_id, source_visit_id, created_at
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findCancellableRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set status = 'CANCELLED', updated_at = :updated
            where id = :id and status = 'CONFIRMED'
            """, nativeQuery = true)
    int markCancelled(@Param("id") UUID id, @Param("updated") Timestamp updated);

    @Modifying
    @Query(value = """
            insert into appointment_audits (id, appointment_id, actor_account_id, actor_type, action,
                old_status, new_status, old_doctor_profile_id, new_doctor_profile_id,
                old_start_time, new_start_time, old_end_time, new_end_time, reason, linked_visit_id,
                source_visit_id, occurred_at, correlation_id)
            values (:auditId, :id, :actor, :actorType, 'CANCELLED', 'CONFIRMED', 'CANCELLED', :doctor, :doctor,
                :oldStart, :newStart, :oldEnd, :newEnd, cast(:reason as varchar), cast(:visit as uuid),
                cast(:sourceVisit as uuid), :occurred, :correlation)
            """, nativeQuery = true)
    int insertCancellationAudit(@Param("auditId") UUID auditId, @Param("id") UUID id,
            @Param("actor") UUID actor, @Param("actorType") String actorType,
            @Param("doctor") UUID doctor, @Param("oldStart") Timestamp oldStart,
            @Param("newStart") Timestamp newStart, @Param("oldEnd") Timestamp oldEnd,
            @Param("newEnd") Timestamp newEnd, @Param("reason") String reason,
            @Param("visit") UUID visit, @Param("sourceVisit") UUID sourceVisit,
            @Param("occurred") Timestamp occurred, @Param("correlation") String correlation);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findCancelledResult(@Param("id") UUID id);

    default Optional<CancellableAppointment> findForUpdate(UUID id) {
        return findCancellableRowsForUpdate(id).stream().findFirst().map(row -> new CancellableAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]),
                (String) row[4], (String) row[5], (String) row[6], AppointmentRowMapper.instant(row[7]),
                AppointmentRowMapper.instant(row[8]), AppointmentRowMapper.integer(row[9]),
                AppointmentRowMapper.integer(row[10]), AppointmentRowMapper.uuid(row[11]),
                AppointmentRowMapper.uuid(row[12]), AppointmentRowMapper.instant(row[13])));
    }

    default CreatedAppointment cancel(CancellableAppointment old, UUID actorAccountId, String actorType,
            String reason, Instant occurredAt, String correlationId) {
        if (markCancelled(old.id(), Timestamp.from(occurredAt)) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded cancellation transaction");
        }
        insertCancellationAudit(UUID.randomUUID(), old.id(), actorAccountId, actorType, old.doctorProfileId(),
                Timestamp.from(old.startTime()), Timestamp.from(old.startTime()),
                Timestamp.from(old.endTime()), Timestamp.from(old.endTime()), reason, old.visitId(),
                old.sourceVisitId(), Timestamp.from(occurredAt), correlationId);
        return AppointmentRowMapper.createdAppointment(findCancelledResult(old.id()).getFirst());
    }
}
