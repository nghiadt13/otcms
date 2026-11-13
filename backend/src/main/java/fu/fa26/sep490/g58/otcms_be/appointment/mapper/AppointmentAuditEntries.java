package fu.fa26.sep490.g58.otcms_be.appointment.mapper;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/** Builds appointment snapshots and preserves the caller's correlation identifier during the write. */
public final class AppointmentAuditEntries {
    private AppointmentAuditEntries() { }

    public static AuditEntry created(AppointmentBooking booking) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", booking.initialStatus());
        state.put("doctorProfileId", booking.doctorProfileId());
        state.put("startTime", booking.startTime());
        state.put("endTime", booking.endTime());
        if (booking.sourceVisitId() != null) state.put("sourceVisitId", booking.sourceVisitId());
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (booking.reason() != null) metadata.put("reason", booking.reason());
        return AuditEntry.builder("appointment_audits", "APPOINTMENT", "CREATED", "APPOINTMENT")
                .entityId(booking.id()).recordLabel(booking.id().toString())
                .actorAccountId(booking.createdByAccountId()).actorType(booking.actorType().name())
                .afterState(state).metadata(metadata).occurredAt(booking.occurredAt()).build();
    }

    public static AuditEntry changed(UUID id, UUID actor, String actorType, String action,
            String oldStatus, String newStatus, UUID oldDoctor, UUID newDoctor,
            Instant oldStart, Instant newStart, Instant oldEnd, Instant newEnd,
            UUID visit, UUID sourceVisit, String reason, UUID verifiedPatient, Instant occurredAt) {
        Map<String, Object> before = state(oldStatus, oldDoctor, oldStart, oldEnd);
        Map<String, Object> after = state(newStatus, newDoctor, newStart, newEnd);
        if (visit != null) after.put("visitId", visit);
        if (sourceVisit != null) after.put("sourceVisitId", sourceVisit);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reason != null) metadata.put("reason", reason);
        if (verifiedPatient != null) metadata.put("verifiedPatientProfileId", verifiedPatient);
        return AuditEntry.builder("appointment_audits", "APPOINTMENT", action, "APPOINTMENT")
                .entityId(id).recordLabel(id.toString()).actorAccountId(actor).actorType(actorType)
                .beforeState(before).afterState(after).metadata(metadata).occurredAt(occurredAt).build();
    }

    private static Map<String, Object> state(String status, UUID doctor, Instant start, Instant end) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", status);
        state.put("doctorProfileId", doctor);
        state.put("startTime", start);
        state.put("endTime", end);
        return state;
    }

    public static void record(AuditLogWriter writer, AuditEntry entry, String correlationId) {
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) MDC.remove(CorrelationIdFilter.MDC_KEY);
        else MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            writer.record(entry);
        } finally {
            if (previous == null) MDC.remove(CorrelationIdFilter.MDC_KEY);
            else MDC.put(CorrelationIdFilter.MDC_KEY, previous);
        }
    }
}
