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
                with queue_rows as (
                    select q.id, q.visit_id, q.arrived_at, q.status
                    from queue_entries q
                    join visits v on v.id = q.visit_id and v.source = 'WALK_IN'
                    where q.stage = 'RECEPTION' and q.status = 'WAITING'
                      and q.arrived_at >= :from and q.arrived_at < :to
                    union all
                    select q.id, q.visit_id, coalesce(v.checked_in_at, q.entered_at), q.status
                    from queue_entries q join visits v on v.id = q.visit_id
                    where q.stage = 'EXAM' and q.status in ('WAITING', 'CALLED', 'ABSENT', 'IN_EXAMINATION', 'AWAITING_CONCLUSION')
                      and coalesce(v.checked_in_at, q.entered_at) >= :from
                      and coalesce(v.checked_in_at, q.entered_at) < :to
                      and not exists (select 1 from queue_entries reception where reception.visit_id = v.id
                          and reception.stage = 'RECEPTION' and reception.status = 'WAITING')
                ), active as (
                    select *, row_number() over (order by arrived_at, id) as position from queue_rows
                )
                select coalesce(exam.id, a.id), a.visit_id, a.arrived_at, coalesce(exam.status, a.status), a.position,
                       coalesce(c.step_code, v.status) as current_stage, coalesce(exam_room.room_code, r.room_code),
                       exam.queue_number, v.originating_appointment_id
                from active a
                join visits v on v.id = a.visit_id
                left join queue_entries exam on exam.visit_id = v.id and exam.stage = 'EXAM'
                left join rooms exam_room on exam_room.id = exam.room_id
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
                       r.room_code,
                       exam.queue_number as exam_queue_number,
                       exam.status as exam_queue_status,
                       exam_room.room_code as exam_room_code
                from visits v
                left join lateral (
                    select step_code from visit_assignments
                    where visit_id = v.id and status = 'IN_PROGRESS'
                    order by starts_at desc, id desc limit 1
                ) c on true
                left join queue_entries q on q.visit_id = v.id and q.stage = 'RECEPTION' and q.status = 'WAITING'
                left join queue_entries exam on exam.visit_id = v.id and exam.stage = 'EXAM'
                left join rooms exam_room on exam_room.id = exam.room_id
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
                        from queue_entries earlier
                        join visits earlier_visit on earlier_visit.id = earlier.visit_id
                            and earlier_visit.source = 'WALK_IN'
                        where earlier.stage = 'RECEPTION' and earlier.status = 'WAITING'
                          and (earlier.arrived_at, earlier.id) < (current.arrived_at, current.id)
                          and earlier.arrived_at >= :from and earlier.arrived_at < :to) + 1
                from queue_entries current
                join visits current_visit on current_visit.id = current.visit_id
                    and current_visit.source = 'WALK_IN'
                where current.id = :queueEntryId and current.stage = 'RECEPTION' and current.status = 'WAITING'
                  and current.arrived_at >= :from and current.arrived_at < :to
                """).setParameter("queueEntryId", queueEntryId).setParameter("from", from)
                .setParameter("to", to).getResultList();
        return rows.isEmpty() ? 0 : rows.getFirst().intValue();
    }

    public Object[] findActiveRoomQueue(UUID visitId, String zone) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select q.queue_number, q.status, r.room_code,
                    (select count(*) from queue_entries ahead
                     where ahead.stage = q.stage and ahead.room_id = q.room_id
                       and ahead.status in ('WAITING', 'CALLED', 'IN_EXAMINATION')
                       and cast(ahead.entered_at at time zone :zone as date) = cast(q.entered_at at time zone :zone as date)
                       and (ahead.sort_order, ahead.entered_at, ahead.id) < (q.sort_order, q.entered_at, q.id)),
                    q.stage
                from queue_entries q left join rooms r on r.id = q.room_id
                where q.visit_id = :visitId and q.stage in ('EXAM', 'SERVICE', 'RESULT_REVIEW')
                  and q.status in ('WAITING', 'CALLED', 'IN_EXAMINATION')
                order by q.entered_at desc, q.id desc limit 1
                """).setParameter("visitId", visitId).setParameter("zone", zone).getResultList();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Mọi hàng phòng đang chờ của lượt (khám bệnh và từng chỉ định): stage, service_order_id, số thứ tự,
     * trạng thái, mã phòng, số người đứng trước. Mới nhất trước.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findActiveQueueEntries(UUID visitId, String zone) {
        return entityManager.createNativeQuery("""
                select q.stage, q.service_order_id, q.queue_number, q.status, r.room_code,
                    (select count(*) from queue_entries ahead
                     where ahead.stage = q.stage and ahead.room_id = q.room_id
                       and ahead.status in ('WAITING', 'CALLED', 'IN_EXAMINATION')
                       and cast(ahead.entered_at at time zone :zone as date) = cast(q.entered_at at time zone :zone as date)
                       and (ahead.sort_order, ahead.entered_at, ahead.id) < (q.sort_order, q.entered_at, q.id))
                from queue_entries q left join rooms r on r.id = q.room_id
                where q.visit_id = :visitId and q.stage in ('EXAM', 'SERVICE')
                  and q.status in ('WAITING', 'CALLED', 'IN_EXAMINATION')
                order by q.entered_at desc, q.id desc
                """).setParameter("visitId", visitId).setParameter("zone", zone).getResultList();
    }

}
