package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VisitReceptionReadRepository {
    private final EntityManager entityManager;

    public VisitReceptionReadRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Object[]> findQueue(Timestamp from, Timestamp to, int offset, int limit) {
        return entityManager.createNativeQuery("""
                with active as (
                    select q.id, q.visit_id, q.arrived_at, q.status,
                           row_number() over (order by q.arrived_at, q.id) as position
                    from walk_in_reception_queue_entries q
                    join visits v on v.id = q.visit_id and v.source = 'WALK_IN'
                    where q.status = 'WAITING' and q.arrived_at >= :from and q.arrived_at < :to
                )
                select a.id, a.visit_id, a.arrived_at, a.status, a.position,
                       coalesce(c.step_code, v.status) as current_stage, r.room_code
                from active a
                join visits v on v.id = a.visit_id
                left join lateral (
                    select step_code from visit_assignments
                    where visit_id = v.id and status = 'IN_PROGRESS'
                    order by starts_at desc, id desc limit 1
                ) c on true
                left join lateral (
                    select room.room_code from room_reservations reservation
                    join rooms room on room.id = reservation.room_id
                    where reservation.visit_id = v.id and reservation.status = 'IN_PROGRESS'
                    order by reservation.assigned_at desc, reservation.id desc limit 1
                ) r on true
                order by a.arrived_at, a.id offset :offset limit :limit
                """)
                .setParameter("from", from).setParameter("to", to)
                .setParameter("offset", offset).setParameter("limit", limit)
                .getResultList();
    }

    public Object[] findProgress(UUID visitId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select v.id, v.patient_profile_id, v.source, v.status, v.arrived_at,
                       coalesce(c.step_code, v.status) as current_stage,
                       q.id as queue_entry_id, q.status as queue_status,
                       r.room_code
                from visits v
                left join lateral (
                    select step_code from visit_assignments
                    where visit_id = v.id and status = 'IN_PROGRESS'
                    order by starts_at desc, id desc limit 1
                ) c on true
                left join walk_in_reception_queue_entries q on q.visit_id = v.id and q.status = 'WAITING'
                left join lateral (
                    select room.room_code from room_reservations reservation
                    join rooms room on room.id = reservation.room_id
                    where reservation.visit_id = v.id and reservation.status = 'IN_PROGRESS'
                    order by reservation.assigned_at desc, reservation.id desc limit 1
                ) r on true
                where v.id = :visitId
                """).setParameter("visitId", visitId).getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int findPosition(UUID queueEntryId, Timestamp from, Timestamp to) {
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                select (select count(*)
                        from walk_in_reception_queue_entries earlier
                        join visits earlier_visit on earlier_visit.id = earlier.visit_id
                            and earlier_visit.source = 'WALK_IN'
                        where earlier.status = 'WAITING'
                          and (earlier.arrived_at, earlier.id) < (current.arrived_at, current.id)
                          and earlier.arrived_at >= :from and earlier.arrived_at < :to) + 1
                from walk_in_reception_queue_entries current
                join visits current_visit on current_visit.id = current.visit_id
                    and current_visit.source = 'WALK_IN'
                where current.id = :queueEntryId and current.status = 'WAITING'
                  and current.arrived_at >= :from and current.arrived_at < :to
                """).setParameter("queueEntryId", queueEntryId).setParameter("from", from)
                .setParameter("to", to).getResultList();
        return rows.isEmpty() ? 0 : rows.getFirst().intValue();
    }

}
