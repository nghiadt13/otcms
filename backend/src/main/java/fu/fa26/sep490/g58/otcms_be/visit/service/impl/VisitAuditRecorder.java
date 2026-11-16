package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * The only writer of visit audit rows in {@code audit_logs} (final-db phase 05). Replaces the tables
 * {@code visit_audits}, {@code visit_assignment_audits} and {@code walk_in_reception_queue_history} (the last one
 * used to be written by a database trigger). The {@code source}, {@code action}, {@code record_type} and
 * {@code dedup_key} values must stay exactly as V115 copies the old rows, so old and new rows read the same in the
 * activity log ({@code activity_log_entries} prefixes ids by source: visit:, care:, queue:).
 *
 * <p>Every method needs the caller's transaction (AuditLogWriter is MANDATORY). The command's correlation id is
 * written through the MDC key the writer reads, then the previous MDC value is restored.
 */
@Component
public class VisitAuditRecorder {
    static final String VISIT_SOURCE = "visit_audits";
    static final String CARE_SOURCE = "visit_assignment_audits";
    static final String QUEUE_SOURCE = "walk_in_reception_queue_history";
    static final String CAPACITY_CONFLICT = "CAPACITY_CONFLICT";

    private final AuditLogWriter auditLog;

    public VisitAuditRecorder(AuditLogWriter auditLog) {
        this.auditLog = auditLog;
    }

    // ---- visit_audits: WALK_IN_CREATED, WALK_IN_CANCELLED, WALK_IN_CLOSED, VISIT_CLOSED -------------------------

    public void walkInCreated(UUID actorAccountId, UUID visitId, Instant occurredAt, String correlationId) {
        visitEvent(actorAccountId, visitId, "WALK_IN_CREATED", occurredAt, correlationId);
    }

    public void walkInCancelled(UUID actorAccountId, UUID visitId, Instant occurredAt, String correlationId) {
        visitEvent(actorAccountId, visitId, "WALK_IN_CANCELLED", occurredAt, correlationId);
    }

    public void walkInClosed(UUID actorAccountId, UUID visitId, Instant occurredAt, String correlationId) {
        visitEvent(actorAccountId, visitId, "WALK_IN_CLOSED", occurredAt, correlationId);
    }

    public void visitClosed(UUID actorAccountId, UUID visitId, Instant occurredAt, String correlationId) {
        visitEvent(actorAccountId, visitId, "VISIT_CLOSED", occurredAt, correlationId);
    }

    private void visitEvent(UUID actorAccountId, UUID visitId, String action, Instant occurredAt,
            String correlationId) {
        // Old columns: actor_account_id, occurred_at and correlation_id were NOT NULL.
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireCorrelation(correlationId);
        write(AuditEntry.builder(VISIT_SOURCE, "VISIT", action, "VISIT")
                .entityId(visitId).recordLabel(visitId.toString())
                .actorAccountId(actorAccountId).occurredAt(occurredAt).build(), correlationId, false);
    }

    // ---- visit_assignment_audits: STARTED, EXTENDED, COMPLETED, CAPACITY_CONFLICT -------------------------------

    /**
     * Care-assignment event (former care-assignment and workflow audit persistence methods).
     * {@code assignmentId} may be null; the row then points at the visit, like V115's COALESCE(assignment_id, visit_id).
     */
    public void careAssignment(UUID visitId, UUID assignmentId, UUID actorAccountId, String action, String stepCode,
            Instant oldExpectedEnd, Instant newExpectedEnd, Instant occurredAt, String correlationId) {
        if (CAPACITY_CONFLICT.equals(action)) {
            throw new IllegalArgumentException("use careCapacityConflict for CAPACITY_CONFLICT");
        }
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireCorrelation(correlationId);
        write(careEntry(visitId, assignmentId, actorAccountId, action, stepCode, oldExpectedEnd, newExpectedEnd,
                occurredAt).build(), correlationId, false);
    }

    /**
     * Capacity conflict, recorded at most once per (visit, actor, correlation): the old table used a partial unique
     * index plus {@code on conflict do nothing}; this uses dedup_key + recordIfAbsent. Key format = V115.
     */
    public void careCapacityConflict(UUID visitId, UUID assignmentId, UUID actorAccountId, String correlationId,
            String attemptedStepCode, Instant occurredAt) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireCorrelation(correlationId);
        write(careEntry(visitId, assignmentId, actorAccountId, CAPACITY_CONFLICT, attemptedStepCode, null, null,
                occurredAt).dedupKey(careCapacityConflictKey(visitId, actorAccountId, correlationId)).build(),
                correlationId, true);
    }

    private static AuditEntry.AuditEntryBuilder careEntry(UUID visitId, UUID assignmentId, UUID actorAccountId,
            String action, String stepCode, Instant oldExpectedEnd, Instant newExpectedEnd, Instant occurredAt) {
        return AuditEntry.builder(CARE_SOURCE, "VISIT", action, "VISIT_ASSIGNMENT")
                .entityId(assignmentId != null ? assignmentId : visitId).recordLabel(visitId.toString())
                .actorAccountId(actorAccountId)
                .beforeState(expectedEnd(oldExpectedEnd)).afterState(expectedEnd(newExpectedEnd))
                .metadata(single("stepCode", stepCode)).occurredAt(occurredAt);
    }

    /** Same text as V115: 'care-capacity:' || sha256(visit_id || ':' || actor_account_id || ':' || correlation_id). */
    static String careCapacityConflictKey(UUID visitId, UUID actorAccountId, String correlationId) {
        try {
            byte[] input = (visitId + ":" + actorAccountId + ":" + correlationId).getBytes(StandardCharsets.UTF_8);
            return "care-capacity:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- walk_in_reception_queue_history (was the trigger record_walk_in_reception_queue_transition) -------------

    /**
     * Reception queue row created (action WAITING). The trigger also required an ACTIVE RECEPTIONIST actor: callers
     * must check that before inserting (CreateWalkInVisitServiceImpl, WalkInReceptionQueueServiceImpl).
     * {@code occurred_at} is the write time, like the trigger's clock_timestamp().
     */
    public void queueEntered(UUID visitId, UUID queueEntryId, UUID actorAccountId, String correlationId) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        write(queueEntry(visitId, queueEntryId, actorAccountId, "WAITING", null, "WAITING"),
                blankToNull(correlationId), false);
    }

    /**
     * Reception queue row closed; action is the exit reason (CARE_STARTED needs a DOCTOR actor, CANCELLED and
     * VISIT_CLOSED a RECEPTIONIST: ExitWalkInReceptionQueueServiceImpl checks that).
     */
    public void queueLeft(UUID visitId, UUID queueEntryId, String exitReason, UUID actorAccountId,
            String correlationId) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(exitReason, "exitReason");
        write(queueEntry(visitId, queueEntryId, actorAccountId, exitReason, "WAITING", "LEFT_RECEPTION"),
                blankToNull(correlationId), false);
    }

    private static AuditEntry queueEntry(UUID visitId, UUID queueEntryId, UUID actorAccountId, String action,
            String fromStatus, String toStatus) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queueEntryId", queueEntryId);
        metadata.put("fromStatus", fromStatus);
        metadata.put("toStatus", toStatus);
        return AuditEntry.builder(QUEUE_SOURCE, "VISIT", action, "VISIT")
                .entityId(visitId).recordLabel(visitId.toString())
                .actorAccountId(actorAccountId).metadata(metadata).build();
    }

    // ---- shared -----------------------------------------------------------------------------------------------

    private void write(AuditEntry entry, String correlationId, boolean ifAbsent) {
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            if (correlationId == null) MDC.remove(CorrelationIdFilter.MDC_KEY);
            else MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            if (ifAbsent) auditLog.recordIfAbsent(entry);
            else auditLog.record(entry);
        } finally {
            if (previous == null) MDC.remove(CorrelationIdFilter.MDC_KEY);
            else MDC.put(CorrelationIdFilter.MDC_KEY, previous);
        }
    }

    /** Old CHECK: 1..128 characters after trim. */
    private static void requireCorrelation(String correlationId) {
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("correlationId must be 1..128 characters");
        }
    }

    /** The trigger stored NULL for an empty correlation setting. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, Object> expectedEnd(Instant value) {
        return single("expectedEnd", value == null ? null : value.toString());
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
