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
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at, payment_deadline from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findCreatedRow(@Param("id") UUID id);

    @Override
    default CreatedAppointment save(AppointmentBooking booking) {
        try {
            saveAndFlush(AppointmentJpaEntity.from(booking));
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
            saveAndFlush(AppointmentJpaEntity.from(booking));
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
