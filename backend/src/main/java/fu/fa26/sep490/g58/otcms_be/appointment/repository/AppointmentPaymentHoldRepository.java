package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.PaymentHoldRow;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence for the online-booking payment hold (AWAITING_PAYMENT -> CONFIRMED | PAYMENT_EXPIRED).
 * Candidate selection never locks rows: callers call billing settle() first (invoice lock) and only then lock the
 * appointment row through {@link #findForUpdate(UUID)} (lock order: invoice before appointment).
 */
public interface AppointmentPaymentHoldRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    String COLUMNS = """
            id, patient_profile_id, service_id, doctor_profile_id, status, start_time, end_time,
            created_by_account_id, payment_deadline
            """;

    @Query(value = "select " + COLUMNS + " from appointments where id = :id for update", nativeQuery = true)
    List<Object[]> findRowsForUpdate(@Param("id") UUID id);

    @Query(value = "select " + COLUMNS + " from appointments where id = :id", nativeQuery = true)
    List<Object[]> findRows(@Param("id") UUID id);

    default Optional<PaymentHoldRow> findForUpdate(UUID id) {
        return findRowsForUpdate(id).stream().findFirst().map(AppointmentPaymentHoldRepository::toRow);
    }

    default Optional<PaymentHoldRow> find(UUID id) {
        return findRows(id).stream().findFirst().map(AppointmentPaymentHoldRepository::toRow);
    }

    private static PaymentHoldRow toRow(Object[] row) {
        return new PaymentHoldRow(AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]), (String) row[4],
                AppointmentRowMapper.instant(row[5]), AppointmentRowMapper.instant(row[6]),
                AppointmentRowMapper.uuid(row[7]), AppointmentRowMapper.instant(row[8]));
    }

    /** AWAITING_PAYMENT -> CONFIRMED; the deadline is cleared because it only describes a live hold. */
    @Modifying
    @Query(value = """
            update appointments set status = 'CONFIRMED', payment_deadline = null, updated_at = :now
            where id = :id and status = 'AWAITING_PAYMENT'
            """, nativeQuery = true)
    int markConfirmed(@Param("id") UUID id, @Param("now") Instant now);

    /** AWAITING_PAYMENT -> PAYMENT_EXPIRED (technical terminal state, frees the slot). */
    @Modifying
    @Query(value = """
            update appointments set status = 'PAYMENT_EXPIRED', updated_at = :now
            where id = :id and status = 'AWAITING_PAYMENT'
            """, nativeQuery = true)
    int markPaymentExpired(@Param("id") UUID id, @Param("now") Instant now);

    /** Sweeper candidates: lapsed holds, oldest deadline first. No row lock is taken. */
    @Query(value = """
            select id from appointments
            where status = 'AWAITING_PAYMENT' and payment_deadline <= :now
            order by payment_deadline, id
            limit :limit offset :offset
            """, nativeQuery = true)
    List<UUID> findLapsedHoldIds(@Param("now") Instant now, @Param("limit") int limit, @Param("offset") int offset);

    /** Lapsed holds of one doctor whose occupied range overlaps [fromInclusive, toExclusive): they would trip the EXCLUDE constraint. */
    @Query(value = """
            select id from appointments
            where status = 'AWAITING_PAYMENT' and payment_deadline <= :now and doctor_profile_id = :doctorId
              and start_time < :toExclusive and occupied_until > :fromInclusive
            order by payment_deadline, id
            """, nativeQuery = true)
    List<UUID> findLapsedHoldIdsOverlapping(@Param("doctorId") UUID doctorId,
            @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive,
            @Param("now") Instant now);

    /** Holds of one booking account (any deadline): at most one should be live, stale ones are released too. */
    @Query(value = """
            select id from appointments
            where status = 'AWAITING_PAYMENT' and created_by_account_id = :accountId
            order by payment_deadline, id
            """, nativeQuery = true)
    List<UUID> findHoldIdsByCreator(@Param("accountId") UUID accountId);

    @Query(value = "select payment_deadline from appointments where id = :id and status = 'AWAITING_PAYMENT'",
            nativeQuery = true)
    List<Object> findHoldDeadline(@Param("id") UUID id);

    @Query(value = """
            select id from appointments
            where id in (:ids) and status in ('AWAITING_PAYMENT', 'PAYMENT_EXPIRED')
            """, nativeQuery = true)
    List<UUID> findHoldOrReleasedIds(@Param("ids") Collection<UUID> ids);

    default Optional<Instant> findLiveHoldDeadline(UUID id) {
        return findHoldDeadline(id).stream().findFirst().map(AppointmentRowMapper::instant);
    }

    default Set<UUID> holdOrReleasedAmong(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return findHoldOrReleasedIds(ids).stream().collect(Collectors.toUnmodifiableSet());
    }
}
