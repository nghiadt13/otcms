package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ReassignableAppointment;
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

public interface AppointmentReassignmentRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, visit_id, source_visit_id, created_at
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findReassignableRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set doctor_profile_id = :newDoctor, updated_at = :updated
            where id = :id and status = 'CONFIRMED'
            """, nativeQuery = true)
    int updateDoctor(@Param("id") UUID id, @Param("newDoctor") UUID newDoctor,
            @Param("updated") Timestamp updated);

    @Query(value = "select account_type from accounts where id = :actor", nativeQuery = true)
    String findAccountType(@Param("actor") UUID actor);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findReassignedResult(@Param("id") UUID id);

    default Optional<ReassignableAppointment> findForUpdate(UUID id) {
        return findReassignableRowsForUpdate(id).stream().findFirst().map(row -> new ReassignableAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]),
                (String) row[4], (String) row[5], (String) row[6], AppointmentRowMapper.instant(row[7]),
                AppointmentRowMapper.instant(row[8]), AppointmentRowMapper.integer(row[9]),
                AppointmentRowMapper.integer(row[10]), AppointmentRowMapper.uuid(row[11]),
                AppointmentRowMapper.uuid(row[12]), AppointmentRowMapper.instant(row[13])));
    }

    default CreatedAppointment reassign(ReassignableAppointment old, UUID newDoctorProfileId,
            UUID actorAccountId, String reason, Instant occurredAt, String correlationId) {
        if (updateDoctor(old.id(), newDoctorProfileId, Timestamp.from(occurredAt)) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded reassignment transaction");
        }
        return AppointmentRowMapper.createdAppointment(findReassignedResult(old.id()).getFirst());
    }
}
