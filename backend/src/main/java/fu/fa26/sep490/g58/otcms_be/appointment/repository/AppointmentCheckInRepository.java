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
            select a.visit_id, r.checkin_checked_in_at
            from appointments a
            left join appointment_arrivals r on r.appointment_id = a.id
            where a.id = :appointment
            """, nativeQuery = true)
    List<Object[]> findCheckInReplayRows(@Param("appointment") UUID appointment);

    @Query(value = """
            select id, patient_profile_id, doctor_profile_id, status, visit_id, source_visit_id, start_time, end_time
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

    /** Check-in group of the arrival row. Runs after markCheckedIn in the same transaction; keeps the arrival group. */
    @Modifying
    @Query(value = """
            insert into appointment_arrivals (appointment_id, checkin_actor_account_id, checkin_checked_in_at)
            values (:appointment, :actor, :updated)
            on conflict (appointment_id) do update set checkin_actor_account_id = excluded.checkin_actor_account_id,
                checkin_checked_in_at = excluded.checkin_checked_in_at
            """, nativeQuery = true)
    int upsertCheckInArrival(@Param("appointment") UUID appointment, @Param("actor") UUID actor,
            @Param("updated") Timestamp updated);

    /** Replay payload of an appointment already checked in; the key itself is looked up in idempotency_records. */
    default Optional<CheckInReplay> findReplay(UUID appointmentId) {
        return findCheckInReplayRows(appointmentId).stream().findFirst()
                .map(row -> new CheckInReplay(AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.instant(row[1])));
    }

    default Optional<CheckInAppointment> findForUpdate(UUID appointmentId) {
        return findCheckInRowsForUpdate(appointmentId).stream().findFirst().map(row -> new CheckInAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), (String) row[3], AppointmentRowMapper.uuid(row[4]),
                AppointmentRowMapper.uuid(row[5]), AppointmentRowMapper.instant(row[6]), AppointmentRowMapper.instant(row[7])));
    }

    default void completeCheckIn(CheckInAppointment appointment, UUID actorAccountId, UUID visitId, Instant checkedInAt) {
        if (markCheckedIn(appointment.id(), appointment.patientProfileId(), visitId,
                Timestamp.from(checkedInAt)) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded check-in transaction");
        }
        upsertCheckInArrival(appointment.id(), actorAccountId, Timestamp.from(checkedInAt));
    }
}
