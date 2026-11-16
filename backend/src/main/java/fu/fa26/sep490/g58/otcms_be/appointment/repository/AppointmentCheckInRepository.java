package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInReplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentCheckInRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select checkin_request_fingerprint, visit_id, checkin_checked_in_at
            from appointments
            where checkin_actor_account_id = :actor and id = :appointment
              and checkin_idempotency_key = :key
            """, nativeQuery = true)
    List<Object[]> findCheckInReplayRows(@Param("actor") UUID actor,
            @Param("appointment") UUID appointment, @Param("key") String key);

    @Query(value = """
            select id, patient_profile_id, doctor_profile_id, status, visit_id, source_visit_id, start_time, end_time
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findCheckInRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set status = 'CHECKED_IN', visit_id = :visit, updated_at = :updated,
                checkin_idempotency_key = :key, checkin_request_fingerprint = :fingerprint,
                checkin_actor_account_id = :actor, checkin_checked_in_at = :updated
            where id = :appointment and patient_profile_id = :patient
              and status = 'CONFIRMED' and visit_id is null
            """, nativeQuery = true)
    int markCheckedIn(@Param("appointment") UUID appointment, @Param("patient") UUID patient,
            @Param("visit") UUID visit, @Param("updated") Timestamp updated, @Param("actor") UUID actor,
            @Param("key") String key, @Param("fingerprint") String fingerprint);

    default Optional<CheckInReplay> findReplay(UUID actorAccountId, UUID appointmentId, String idempotencyKey) {
        return findCheckInReplayRows(actorAccountId, appointmentId, idempotencyKey).stream().findFirst()
                .map(row -> new CheckInReplay((String) row[0], AppointmentRowMapper.uuid(row[1]),
                        AppointmentRowMapper.instant(row[2])));
    }

    default Optional<CheckInAppointment> findForUpdate(UUID appointmentId) {
        return findCheckInRowsForUpdate(appointmentId).stream().findFirst().map(row -> new CheckInAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), (String) row[3], AppointmentRowMapper.uuid(row[4]),
                AppointmentRowMapper.uuid(row[5]), AppointmentRowMapper.instant(row[6]), AppointmentRowMapper.instant(row[7])));
    }

    default void completeCheckIn(CheckInAppointment appointment, UUID actorAccountId,
            String idempotencyKey, String requestFingerprint, UUID visitId, Instant checkedInAt) {
        if (markCheckedIn(appointment.id(), appointment.patientProfileId(), visitId,
                Timestamp.from(checkedInAt), actorAccountId, idempotencyKey, requestFingerprint) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded check-in transaction");
        }
    }
}
