package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentBookingRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentJpaEntity, UUID>, AppointmentBookingRepository {
    @Modifying
    @Query(value = """
            insert into appointment_audits (id, appointment_id, actor_account_id, actor_type, action,
                new_status, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id)
            values (:id, :appointmentId, :actorAccountId, :actorType, 'CREATED', 'CONFIRMED',
                :doctorProfileId, :startTime, :endTime, :occurredAt, :correlationId)
            """, nativeQuery = true)
    int insertCreationAudit(@Param("id") UUID id, @Param("appointmentId") UUID appointmentId,
            @Param("actorAccountId") UUID actorAccountId, @Param("actorType") String actorType,
            @Param("doctorProfileId") UUID doctorProfileId, @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime, @Param("occurredAt") Instant occurredAt,
            @Param("correlationId") String correlationId);

    @Modifying
    @Query(value = """
            insert into appointment_audits (id, appointment_id, actor_account_id, actor_type, action,
                new_status, new_doctor_profile_id, new_start_time, new_end_time, reason, source_visit_id,
                occurred_at, correlation_id)
            values (:id, :appointmentId, :actorAccountId, 'DOCTOR', 'CREATED', 'CONFIRMED',
                :doctorProfileId, :startTime, :endTime, :reason, :sourceVisitId, :occurredAt, :correlationId)
            """, nativeQuery = true)
    int insertFollowUpCreationAudit(@Param("id") UUID id, @Param("appointmentId") UUID appointmentId,
            @Param("actorAccountId") UUID actorAccountId, @Param("doctorProfileId") UUID doctorProfileId,
            @Param("startTime") Instant startTime, @Param("endTime") Instant endTime,
            @Param("reason") String reason, @Param("sourceVisitId") UUID sourceVisitId,
            @Param("occurredAt") Instant occurredAt, @Param("correlationId") String correlationId);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findCreatedRow(@Param("id") UUID id);

    @Override
    default CreatedAppointment save(AppointmentBooking booking) {
        try {
            saveAndFlush(AppointmentJpaEntity.confirmed(booking));
            insertCreationAudit(UUID.randomUUID(), booking.id(), booking.createdByAccountId(),
                    booking.actorType().name(), booking.doctorProfileId(), booking.startTime(),
                    booking.endTime(), booking.occurredAt(), booking.correlationId());
            return AppointmentRowMapper.createdAppointment(findCreatedRow(booking.id()).getFirst());
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23P01")) {
                throw new AppointmentBookingException("SLOT_UNAVAILABLE", exception);
            }
            if (hasConstraint(exception, "appointments_patient_profile_id_fkey")) {
                throw new AppointmentBookingException("PATIENT_PROFILE_NOT_FOUND", exception);
            }
            throw exception;
        }
    }

    @Override
    default CreatedAppointment saveFollowUp(AppointmentBooking booking) {
        try {
            saveAndFlush(AppointmentJpaEntity.confirmed(booking));
            insertFollowUpCreationAudit(UUID.randomUUID(), booking.id(), booking.createdByAccountId(),
                    booking.doctorProfileId(), booking.startTime(), booking.endTime(), booking.reason(),
                    booking.sourceVisitId(), booking.occurredAt(), booking.correlationId());
            return AppointmentRowMapper.createdAppointment(findCreatedRow(booking.id()).getFirst());
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23P01")) {
                throw new AppointmentBookingException("SLOT_UNAVAILABLE", exception);
            }
            if (hasConstraint(exception, "appointments_patient_profile_id_fkey")) {
                throw new AppointmentBookingException("PATIENT_PROFILE_NOT_FOUND", exception);
            }
            if (hasConstraint(exception, "appointments_source_visit_id_fkey")
                    || hasConstraint(exception, "fk_appointments_source_visit_patient")) {
                throw new AppointmentBookingException("SOURCE_VISIT_NOT_FOUND", exception);
            }
            throw exception;
        }
    }

    private static boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && constraint.equals(violation.getConstraintName())) return true;
            if (current instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if (next.getMessage() != null && next.getMessage().contains(constraint)) return true;
                }
            }
        }
        return false;
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
