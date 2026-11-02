package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ReschedulableAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRescheduleRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, purpose, start_time, end_time,
                   duration_minutes, buffer_minutes, booking_source, visit_id, source_visit_id, created_at
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findReschedulableRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set start_time = :start, end_time = :end, updated_at = :updated
            where id = :id and status = 'CONFIRMED'
            """, nativeQuery = true)
    int updateSchedule(@Param("id") UUID id, @Param("start") Timestamp start,
            @Param("end") Timestamp end, @Param("updated") Timestamp updated);

    @Modifying
    @Query(value = """
            insert into appointment_audits (appointment_id, actor_account_id, actor_type, action,
                old_status, new_status, old_doctor_profile_id, new_doctor_profile_id,
                old_start_time, new_start_time, old_end_time, new_end_time, reason, occurred_at, correlation_id)
            values (:id, :actor, :actorType, 'RESCHEDULED', 'CONFIRMED', 'CONFIRMED', :doctor, :doctor,
                :oldStart, :newStart, :oldEnd, :newEnd, :reason, :occurred, :correlation)
            """, nativeQuery = true)
    int insertRescheduleAudit(@Param("id") UUID id, @Param("actor") UUID actor,
            @Param("actorType") String actorType, @Param("doctor") UUID doctor,
            @Param("oldStart") Timestamp oldStart, @Param("newStart") Timestamp newStart,
            @Param("oldEnd") Timestamp oldEnd, @Param("newEnd") Timestamp newEnd,
            @Param("reason") String reason, @Param("occurred") Timestamp occurred,
            @Param("correlation") String correlation);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findRescheduledResult(@Param("id") UUID id);

    default Optional<ReschedulableAppointment> findForUpdate(UUID id) {
        return findReschedulableRowsForUpdate(id).stream().findFirst().map(row -> new ReschedulableAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]),
                (String) row[4], (String) row[5], AppointmentRowMapper.instant(row[6]),
                AppointmentRowMapper.instant(row[7]), AppointmentRowMapper.integer(row[8]),
                AppointmentRowMapper.integer(row[9]), (String) row[10],
                AppointmentRowMapper.uuid(row[11]), AppointmentRowMapper.uuid(row[12]),
                AppointmentRowMapper.instant(row[13])));
    }

    default CreatedAppointment reschedule(ReschedulableAppointment old, Instant start, Instant end,
            UUID actorAccountId, String actorType, String reason, Instant occurredAt, String correlationId) {
        try {
            if (updateSchedule(old.id(), Timestamp.from(start), Timestamp.from(end), Timestamp.from(occurredAt)) != 1) {
                throw new IllegalStateException("Appointment changed inside guarded reschedule transaction");
            }
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23P01")) throw new AppointmentBookingException("SLOT_UNAVAILABLE", exception);
            throw exception;
        }
        insertRescheduleAudit(old.id(), actorAccountId, actorType, old.doctorProfileId(),
                Timestamp.from(old.startTime()), Timestamp.from(start), Timestamp.from(old.endTime()),
                Timestamp.from(end), reason, Timestamp.from(occurredAt), correlationId);
        return AppointmentRowMapper.createdAppointment(findRescheduledResult(old.id()).getFirst());
    }

    private static boolean hasSqlState(Throwable failure, String state) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if (state.equals(next.getSQLState())) return true;
                }
            }
        }
        return false;
    }
}
