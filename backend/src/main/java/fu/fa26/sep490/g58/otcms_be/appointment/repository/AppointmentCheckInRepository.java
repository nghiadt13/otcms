package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInReplay;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
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
            select request_fingerprint, result_visit_id, result_checked_in_at
            from appointment_checkin_requests
            where actor_account_id = :actor and appointment_id = :appointment
              and command = 'CHECK_IN' and idempotency_key = :key
            """, nativeQuery = true)
    List<Object[]> findCheckInReplayRows(@Param("actor") UUID actor,
            @Param("appointment") UUID appointment, @Param("key") String key);

    @Query(value = """
            select id, patient_profile_id, doctor_profile_id, status, visit_id, source_visit_id, start_time
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findCheckInRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set status = 'CHECKED_IN', visit_id = :visit, updated_at = :updated
            where id = :appointment and patient_profile_id = :patient
              and status = 'CONFIRMED' and visit_id is null
            """, nativeQuery = true)
    int markCheckedIn(@Param("appointment") UUID appointment, @Param("patient") UUID patient,
            @Param("visit") UUID visit, @Param("updated") Timestamp updated);

    @Modifying
    @Query(value = """
            insert into appointment_audits (id, appointment_id, actor_account_id, actor_type, action,
                old_status, new_status, old_doctor_profile_id, new_doctor_profile_id,
                old_start_time, new_start_time, old_end_time, new_end_time, linked_visit_id,
                source_visit_id, occurred_at, correlation_id, verified_patient_profile_id)
            select :auditId, id, :actor, 'RECEPTIONIST', 'STATUS_CHANGED', 'CONFIRMED', 'CHECKED_IN',
                doctor_profile_id, doctor_profile_id, start_time, start_time, end_time, end_time,
                visit_id, source_visit_id, :occurred, :correlation, :verifiedPatient
            from appointments where id = :appointment
            """, nativeQuery = true)
    int insertCheckInAudit(@Param("auditId") UUID auditId, @Param("actor") UUID actor,
            @Param("occurred") Timestamp occurred, @Param("correlation") String correlation,
            @Param("appointment") UUID appointment, @Param("verifiedPatient") UUID verifiedPatient);

    @Modifying
    @Query(value = """
            insert into appointment_checkin_requests (actor_account_id, appointment_id, command,
                idempotency_key, request_fingerprint, result_visit_id, result_checked_in_at)
            values (:actor, :appointment, 'CHECK_IN', :key, :fingerprint, :visit, :checkedInAt)
            """, nativeQuery = true)
    int insertCheckInReplay(@Param("actor") UUID actor, @Param("appointment") UUID appointment,
            @Param("key") String key, @Param("fingerprint") String fingerprint,
            @Param("visit") UUID visit, @Param("checkedInAt") Timestamp checkedInAt);

    default Optional<CheckInReplay> findReplay(UUID actorAccountId, UUID appointmentId, String idempotencyKey) {
        return findCheckInReplayRows(actorAccountId, appointmentId, idempotencyKey).stream().findFirst()
                .map(row -> new CheckInReplay((String) row[0], AppointmentRowMapper.uuid(row[1]),
                        AppointmentRowMapper.instant(row[2])));
    }

    default Optional<CheckInAppointment> findForUpdate(UUID appointmentId) {
        return findCheckInRowsForUpdate(appointmentId).stream().findFirst().map(row -> new CheckInAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), (String) row[3], AppointmentRowMapper.uuid(row[4]),
                AppointmentRowMapper.uuid(row[5]), AppointmentRowMapper.instant(row[6])));
    }

    default CheckedInAppointment completeCheckIn(CheckInAppointment appointment, UUID actorAccountId,
            UUID verifiedPatientProfileId,
            String idempotencyKey, String requestFingerprint, UUID visitId, Instant checkedInAt,
            String correlationId) {
        if (markCheckedIn(appointment.id(), appointment.patientProfileId(), visitId,
                Timestamp.from(checkedInAt)) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded check-in transaction");
        }
        insertCheckInAudit(UUID.randomUUID(), actorAccountId, Timestamp.from(checkedInAt),
                correlationId, appointment.id(), verifiedPatientProfileId);
        insertCheckInReplay(actorAccountId, appointment.id(), idempotencyKey, requestFingerprint,
                visitId, Timestamp.from(checkedInAt));
        return new CheckedInAppointment(appointment.id(), visitId, checkedInAt);
    }
}
