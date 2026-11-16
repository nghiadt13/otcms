package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VisitRoomReservationRepository {
    private final EntityManager entityManager;
    private final AuditLogWriter auditLog;

    public VisitRoomReservationRepository(EntityManager entityManager, AuditLogWriter auditLog) {
        this.entityManager = entityManager;
        this.auditLog = auditLog;
    }

    public RoomReservationPlan findAvailableRoom(String stepCode, String capabilityCode, Instant startsAt,
            Instant expectedEndsAt, Instant reservedUntil, int cleanupBufferMinutes, UUID excludedAssignmentId) {
        String assignmentExclusion = excludedAssignmentId == null ? ""
                : "and not (x.assignment_id = :excludedAssignmentId and x.status = 'IN_PROGRESS')";
        var query = entityManager.createNativeQuery(("""
                select r.id, r.room_code
                from rooms r
                where r.active = true and r.capabilities @> ARRAY[cast(:capability as text)]
                  and not exists (
                    select 1 from room_reservations x
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
                insert into room_reservations
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
                        update room_reservations set step_code = :stepCode, capability_code = :capability,
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
                    update room_reservations set status = :status, actual_ended_at = :actualEnd,
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
                from room_reservations where assignment_id = :assignmentId and status = 'IN_PROGRESS'
                order by id for update
                """).setParameter("assignmentId", assignmentId).getResultList();
        return rows;
    }

    private void closeAsCleaning(UUID reservationId, Instant actualEndedAt) {
        entityManager.createNativeQuery("""
                update room_reservations
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
        // The legacy audit table required both fields for every reservation event.
        java.util.Objects.requireNonNull(actorAccountId, "actorAccountId");
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("visitId", visitId); metadata.put("assignmentId", assignmentId);
        metadata.put("roomId", roomId); metadata.put("stepCode", stepCode);
        var entry = AuditEntry.builder("room_reservation_audits", "ROOM", action, "ROOM_RESERVATION")
                .entityId(reservationId != null ? reservationId : visitId).recordLabel(visitId.toString())
                .actorAccountId(actorAccountId).metadata(metadata).occurredAt(occurredAt);
        boolean deduplicate = "CAPACITY_CONFLICT".equals(action);
        if (deduplicate) entry.dedupKey(capacityConflictKey(visitId, actorAccountId, correlationId));
        String mdcKey = fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.MDC_KEY;
        String previous = org.slf4j.MDC.get(mdcKey);
        try {
            if (correlationId == null) org.slf4j.MDC.remove(mdcKey);
            else org.slf4j.MDC.put(mdcKey, correlationId);
            if (deduplicate) auditLog.recordIfAbsent(entry.build());
            else auditLog.record(entry.build());
        } finally {
            if (previous == null) org.slf4j.MDC.remove(mdcKey);
            else org.slf4j.MDC.put(mdcKey, previous);
        }
    }

    private static String capacityConflictKey(UUID visitId, UUID actorId, String correlationId) {
        try {
            byte[] input = (visitId + ":" + actorId + ":" + correlationId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return "room-capacity:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
