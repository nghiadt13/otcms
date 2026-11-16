package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.NoShowAppointment;
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

public interface AppointmentNoShowRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, doctor_profile_id, status, start_time, end_time, visit_id, source_visit_id
            from appointments where id = :id for update
            """, nativeQuery = true)
    List<Object[]> findNoShowRowsForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update appointments set status = 'NO_SHOW', updated_at = :updated
            where id = :id and status = 'CONFIRMED' and visit_id is null
            """, nativeQuery = true)
    int updateNoShow(@Param("id") UUID id, @Param("updated") Timestamp updated);

    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose,
                   start_time, end_time, duration_minutes, buffer_minutes, occupied_until, visit_id,
                   source_visit_id, created_at, updated_at from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findNoShowResult(@Param("id") UUID id);

    default Optional<NoShowAppointment> findForUpdate(UUID id) {
        return findNoShowRowsForUpdate(id).stream().findFirst().map(row -> new NoShowAppointment(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), (String) row[3], AppointmentRowMapper.instant(row[4]),
                AppointmentRowMapper.instant(row[5]), AppointmentRowMapper.uuid(row[6]),
                AppointmentRowMapper.uuid(row[7])));
    }

    default CreatedAppointment markNoShow(NoShowAppointment old, UUID actor, String reason,
            Instant occurredAt, String correlationId) {
        return markNoShow(old, actor, "RECEPTIONIST", reason, occurredAt, correlationId);
    }

    /** @param actorType RECEPTIONIST for a manual no-show, SYSTEM for the automatic sweep after the shift ends */
    default CreatedAppointment markNoShow(NoShowAppointment old, UUID actor, String actorType, String reason,
            Instant occurredAt, String correlationId) {
        if (updateNoShow(old.id(), Timestamp.from(occurredAt)) != 1) {
            throw new IllegalStateException("Appointment changed inside guarded no-show transaction");
        }
        return AppointmentRowMapper.createdAppointment(findNoShowResult(old.id()).getFirst());
    }

    /**
     * Auto no-show candidates (BR-64): CONFIRMED, not checked in, started inside the look-back window. Read without a
     * lock; each one is re-checked under its row lock. Paged by offset because rows still inside their shift stay in
     * the result set.
     */
    @Query(value = """
            select id from appointments
            where status = 'CONFIRMED' and visit_id is null and start_time < :now and start_time >= :since
            order by start_time, id
            limit :limit offset :offset
            """, nativeQuery = true)
    List<UUID> findNoShowCandidateIds(@Param("now") Instant now, @Param("since") Instant since,
            @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "select created_by_account_id from appointments where id = :id", nativeQuery = true)
    List<UUID> findCreatedByAccountIds(@Param("id") UUID id);

    /**
     * Account recorded on a SYSTEM audit row: the one that created the booking (audit actor_account_id is
     * NOT NULL; the payment-hold sweep uses the same convention).
     */
    default UUID createdByAccountId(UUID id) {
        return findCreatedByAccountIds(id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Appointment vanished inside guarded no-show transaction"));
    }
}
