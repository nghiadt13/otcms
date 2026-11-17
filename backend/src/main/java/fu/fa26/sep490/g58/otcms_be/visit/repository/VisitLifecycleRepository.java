package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Native SQL for the visit lifecycle (P05): Incomplete sweep, end-of-day close, return within 7 days, room and
 * doctor transfers. Writes are guarded UPDATEs; callers hold the visit row lock and the coordination guard.
 */
@Repository
public class VisitLifecycleRepository {
    /** Open states of the exam queue that the end-of-day close ends (NF-05a). */
    private static final String OPEN_EXAM = "('PENDING_PAYMENT', 'WAITING', 'CALLED', 'ABSENT', 'IN_EXAMINATION')";
    private final EntityManager entityManager;

    public VisitLifecycleRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** OPEN visits Awaiting Conclusion whose first check-in day is on or before {@code lastDueCheckInDay}. */
    @SuppressWarnings("unchecked")
    public List<UUID> findIncompleteCandidates(LocalDate lastDueCheckInDay, ZoneId zone) {
        return entityManager.createNativeQuery("""
                select v.id from visits v
                where v.status = 'OPEN'
                  and cast(timezone(cast(:zone as text), v.checked_in_at) as date) <= :cutoff
                  and exists (select 1 from queue_entries q where q.visit_id = v.id
                              and q.stage = 'EXAM' and q.status = 'AWAITING_CONCLUSION')
                order by v.checked_in_at, v.id
                """, UUID.class).setParameter("zone", zone.getId()).setParameter("cutoff", lastDueCheckInDay)
                .getResultList();
    }

    /** OPEN visits that still have an open queue entry of any stage. */
    @SuppressWarnings("unchecked")
    public List<UUID> findEndOfDayCandidates() {
        return entityManager.createNativeQuery("""
                select v.id from visits v
                where v.status = 'OPEN' and exists (
                    select 1 from queue_entries q where q.visit_id = v.id and (
                        (q.stage = 'EXAM' and q.status in %s)
                        or (q.stage = 'SERVICE' and q.status not in ('COMPLETED', 'CANCELLED'))
                        or (q.stage = 'RECEPTION' and q.status = 'WAITING')))
                order by v.checked_in_at, v.id
                """.formatted(OPEN_EXAM), UUID.class).getResultList();
    }

    public boolean markIncomplete(UUID visitId, Instant now) {
        return entityManager.createNativeQuery("""
                update visits set status = 'INCOMPLETE', incomplete_at = :now, updated_at = :now
                where id = :visitId and status = 'OPEN'
                """).setParameter("visitId", visitId).setParameter("now", Timestamp.from(now)).executeUpdate() == 1;
    }

    /** Closes the open SERVICE queue entries of the visit (the day ended, or the visit became Incomplete). */
    public int closeServiceEntries(UUID visitId, Instant now) {
        return entityManager.createNativeQuery("""
                update queue_entries set status = 'CANCELLED', finished_at = :now
                where visit_id = :visitId and stage = 'SERVICE' and status not in ('COMPLETED', 'CANCELLED')
                """).setParameter("visitId", visitId).setParameter("now", Timestamp.from(now)).executeUpdate();
    }

    /** Releases care that is still reserved or running when nobody will conclude the visit any more. */
    public int releaseOpenCare(UUID visitId, Instant now) {
        return entityManager.createNativeQuery("""
                update visit_assignments set status = 'RELEASED', released_at = :now
                where visit_id = :visitId and status in ('RESERVED', 'IN_PROGRESS')
                """).setParameter("visitId", visitId).setParameter("now", Timestamp.from(now)).executeUpdate();
    }

    /** Care of the visit passes to the new attending doctor (UC-31 AF2). */
    public int reassignOpenCare(UUID visitId, UUID newDoctorProfileId) {
        return entityManager.createNativeQuery("""
                update visit_assignments set doctor_profile_id = :doctor
                where visit_id = :visitId and status in ('RESERVED', 'IN_PROGRESS')
                """).setParameter("visitId", visitId).setParameter("doctor", newDoctorProfileId).executeUpdate();
    }

    /** Status of the SERVICE queue entry of an order, if the patient is already in a room queue for it. */
    @SuppressWarnings("unchecked")
    public Optional<String> findServiceEntryStatus(UUID serviceOrderId) {
        List<String> rows = entityManager.createNativeQuery("""
                select status from queue_entries
                where service_order_id = :orderId and stage = 'SERVICE' order by entered_at desc limit 1
                """, String.class).setParameter("orderId", serviceOrderId).getResultList();
        return rows.stream().findFirst();
    }

    /** Moves the order's open SERVICE entry (waiting or absent) to another room; sort_order is kept. */
    public int moveServiceEntry(UUID serviceOrderId, UUID newRoomId) {
        return entityManager.createNativeQuery("""
                update queue_entries set room_id = :roomId
                where service_order_id = :orderId and stage = 'SERVICE' and status in ('WAITING', 'ABSENT')
                """).setParameter("orderId", serviceOrderId).setParameter("roomId", newRoomId).executeUpdate();
    }

    /**
     * The patient's newest OPEN visit that is Awaiting Conclusion and whose 7-day period has not ended
     * ({@code firstDueDay}: earliest check-in day that is still inside the period). Columns: visit id,
     * checked_in_at, exam doctor, exam room, queue number.
     */
    @SuppressWarnings("unchecked")
    public Optional<Object[]> findReturnCandidate(UUID patientProfileId, LocalDate earliestCheckInDay, ZoneId zone) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                select v.id, v.checked_in_at, q.doctor_profile_id, q.room_id, q.queue_number
                from visits v join queue_entries q on q.visit_id = v.id and q.stage = 'EXAM'
                where v.patient_profile_id = :patient and v.status = 'OPEN' and q.status = 'AWAITING_CONCLUSION'
                  and cast(timezone(cast(:zone as text), v.checked_in_at) as date) >= :earliest
                order by v.checked_in_at desc, v.id desc limit 1
                """).setParameter("patient", patientProfileId).setParameter("zone", zone.getId())
                .setParameter("earliest", earliestCheckInDay).getResultList();
        return rows.stream().findFirst();
    }

    /** People waiting (WAITING or CALLED) in each of the rooms for the clinic day. */
    @SuppressWarnings("unchecked")
    public Map<UUID, Long> countWaitingByRoom(Collection<UUID> roomIds, LocalDate clinicDate) {
        if (roomIds.isEmpty()) return Map.of();
        List<Object[]> rows = entityManager.createNativeQuery("""
                select q.room_id, count(*) from queue_entries q
                where q.stage = 'EXAM' and q.room_id in (:roomIds) and q.clinic_date = :day
                  and q.status in ('WAITING', 'CALLED')
                group by q.room_id
                """).setParameter("roomIds", roomIds).setParameter("day", clinicDate).getResultList();
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
    }
}
