package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VisitRoomReservationRepository {
    private final EntityManager entityManager;

    public VisitRoomReservationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public RoomReservationPlan findAvailableRoom(String stepCode, String capabilityCode, Instant startsAt,
            Instant expectedEndsAt, Instant reservedUntil, int cleanupBufferMinutes, UUID excludedAssignmentId) {
        String assignmentExclusion = excludedAssignmentId == null ? ""
                : "and not (x.assignment_id = :excludedAssignmentId and x.status = 'IN_PROGRESS')";
        var query = entityManager.createNativeQuery(("""
                select r.id, r.room_code
                from visit_rooms r
                join visit_room_capabilities c on c.room_id = r.id
                where r.active = true and c.capability_code = :capability
                  and not exists (
                    select 1 from visit_room_reservations x
                    where x.room_id = r.id
                      /* assignment exclusion */
                      and (x.status = 'IN_PROGRESS'
                           or (x.status = 'CLEANING' and x.reserved_until > :startsAt))
                      and x.starts_at < :reservedUntil
                  )
                order by r.room_code, r.id
                limit 1 for update of r
                """).replace("/* assignment exclusion */", assignmentExclusion))
                .setParameter("capability", capabilityCode)
                .setParameter("reservedUntil", Timestamp.from(reservedUntil))
                .setParameter("startsAt", Timestamp.from(startsAt));
        if (excludedAssignmentId != null) {
            query.setParameter("excludedAssignmentId", excludedAssignmentId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new RoomReservationPlan((UUID) row[0], (String) row[1], stepCode, capabilityCode,
                startsAt, expectedEndsAt, reservedUntil, cleanupBufferMinutes);
    }

    public void insert(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt) {
        UUID reservationId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                insert into visit_room_reservations
                    (id, visit_id, assignment_id, room_id, step_code, capability_code, starts_at,
                     expected_ends_at, reserved_until, cleanup_buffer_minutes, status, assigned_at)
                values (:id, :visitId, :assignmentId, :roomId, :stepCode, :capability, :startsAt,
                        :expectedEndsAt, :reservedUntil, :buffer, 'IN_PROGRESS', :assignedAt)
                """)
                .setParameter("id", reservationId).setParameter("visitId", visitId)
                .setParameter("assignmentId", assignmentId).setParameter("roomId", plan.roomId())
                .setParameter("stepCode", plan.stepCode()).setParameter("capability", plan.capabilityCode())
                .setParameter("startsAt", Timestamp.from(plan.startsAt()))
                .setParameter("expectedEndsAt", Timestamp.from(plan.expectedEndsAt()))
                .setParameter("reservedUntil", Timestamp.from(plan.reservedUntil()))
                .setParameter("buffer", plan.cleanupBufferMinutes()).setParameter("assignedAt", Timestamp.from(occurredAt))
                .executeUpdate();
        appendAudit(visitId, assignmentId, reservationId, plan.roomId(), actorAccountId, "RESERVED",
                plan.stepCode(), correlationId, occurredAt);
    }

    public void extend(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt) {
        List<Object[]> active = lockActiveReservations(assignmentId);
        Object[] sameRoom = active.stream().filter(row -> plan != null && plan.roomId().equals(row[1]))
                .findFirst().orElse(null);
        for (Object[] row : active) {
            UUID reservationId = (UUID) row[0];
            if (sameRoom != null && reservationId.equals(sameRoom[0])) continue;
            closeAsCleaning(reservationId, plan == null ? occurredAt : plan.startsAt());
            appendAudit(visitId, assignmentId, reservationId, (UUID) row[1], actorAccountId,
                    "COMPLETED", (String) row[2], correlationId,
                    plan == null ? occurredAt : plan.startsAt());
        }
        if (plan != null) {
            if (sameRoom == null) {
                insert(plan, visitId, assignmentId, actorAccountId, correlationId, occurredAt);
            } else {
                UUID reservationId = (UUID) sameRoom[0];
                entityManager.createNativeQuery("""
                        update visit_room_reservations set step_code = :stepCode, capability_code = :capability,
                            expected_ends_at = :expectedEnd, reserved_until = :reservedUntil,
                            cleanup_buffer_minutes = :buffer
                        where id = :id and status = 'IN_PROGRESS'
                        """)
                        .setParameter("stepCode", plan.stepCode()).setParameter("capability", plan.capabilityCode())
                        .setParameter("expectedEnd", Timestamp.from(plan.expectedEndsAt()))
                        .setParameter("reservedUntil", Timestamp.from(plan.reservedUntil()))
                        .setParameter("buffer", plan.cleanupBufferMinutes()).setParameter("id", reservationId)
                        .executeUpdate();
                appendAudit(visitId, assignmentId, reservationId, plan.roomId(), actorAccountId, "EXTENDED",
                        plan.stepCode(), correlationId, occurredAt);
            }
        }
    }

    public void complete(UUID visitId, UUID assignmentId, UUID actorAccountId, String correlationId,
            Instant actualEndedAt) {
        for (Object[] row : lockActiveReservations(assignmentId)) {
            UUID reservationId = (UUID) row[0];
            UUID roomId = (UUID) row[1];
            String stepCode = (String) row[2];
            int buffer = ((Number) row[3]).intValue();
            Instant until;
            try {
                until = actualEndedAt.plusSeconds(Math.multiplyExact((long) buffer, 60));
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Room cleanup interval is out of range", exception);
            }
            entityManager.createNativeQuery("""
                    update visit_room_reservations set status = :status, actual_ended_at = :actualEnd,
                        reserved_until = :reservedUntil
                    where id = :id and status = 'IN_PROGRESS'
                    """)
                    .setParameter("status", buffer == 0 ? "RELEASED" : "CLEANING")
                    .setParameter("actualEnd", Timestamp.from(actualEndedAt))
                    .setParameter("reservedUntil", Timestamp.from(until)).setParameter("id", reservationId)
                    .executeUpdate();
            appendAudit(visitId, assignmentId, reservationId, roomId, actorAccountId, "COMPLETED",
                    stepCode, correlationId, actualEndedAt);
        }
    }

    public void appendConflict(UUID visitId, UUID assignmentId, UUID actorAccountId, String stepCode,
            String correlationId, Instant occurredAt) {
        appendAudit(visitId, assignmentId, null, null, actorAccountId, "CAPACITY_CONFLICT",
                stepCode, correlationId, occurredAt);
    }

    private List<Object[]> lockActiveReservations(UUID assignmentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select id, room_id, step_code, cleanup_buffer_minutes
                from visit_room_reservations where assignment_id = :assignmentId and status = 'IN_PROGRESS'
                order by id for update
                """).setParameter("assignmentId", assignmentId).getResultList();
        return rows;
    }

    private void closeAsCleaning(UUID reservationId, Instant actualEndedAt) {
        entityManager.createNativeQuery("""
                update visit_room_reservations
                set status = case when cleanup_buffer_minutes = 0 then 'RELEASED' else 'CLEANING' end,
                    actual_ended_at = :actualEnd,
                    reserved_until = greatest(reserved_until,
                        :actualEnd + make_interval(mins => cleanup_buffer_minutes))
                where id = :id and status = 'IN_PROGRESS'
                """)
                .setParameter("actualEnd", Timestamp.from(actualEndedAt)).setParameter("id", reservationId)
                .executeUpdate();
    }

    private void appendAudit(UUID visitId, UUID assignmentId, UUID reservationId, UUID roomId,
            UUID actorAccountId, String action, String stepCode, String correlationId, Instant occurredAt) {
        entityManager.createNativeQuery("""
                insert into visit_room_reservation_audits
                    (id, visit_id, assignment_id, reservation_id, room_id, actor_account_id,
                     action, step_code, occurred_at, correlation_id)
                values (:id, :visitId, :assignmentId, :reservationId, :roomId, :actorId,
                        :action, :stepCode, :occurredAt, :correlationId)
                on conflict do nothing
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("visitId", visitId)
                .setParameter("assignmentId", assignmentId).setParameter("reservationId", reservationId)
                .setParameter("roomId", roomId).setParameter("actorId", actorAccountId)
                .setParameter("action", action).setParameter("stepCode", stepCode)
                .setParameter("occurredAt", Timestamp.from(occurredAt)).setParameter("correlationId", correlationId)
                .executeUpdate();
    }
}
