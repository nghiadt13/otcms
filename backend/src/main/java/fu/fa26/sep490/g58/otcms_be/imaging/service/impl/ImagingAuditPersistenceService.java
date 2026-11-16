package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes PHI-safe imaging audit events to audit_logs in the caller's business transaction. */
@Service
public class ImagingAuditPersistenceService {
    private static final Set<String> SAFE_METADATA_KEYS = Set.of("purpose", "expiresAt", "state",
            "payloadSizeBytes", "instanceCount", "evidenceCodes", "eventId", "payloadSha256",
            "reasonCode", "resourceType", "decisionCode", "resultCount", "caseId");
    private final AuditLogWriter auditWriter;

    public ImagingAuditPersistenceService(AuditLogWriter auditWriter) { this.auditWriter = auditWriter; }

    @Transactional
    public void recordActor(UUID actorId, String action, String outcome, UUID operationId, String studyRef,
            UUID requestId, UUID attemptId, UUID visitId, UUID patientId, String correlationId,
            Map<String, Object> safeMetadata) {
        record(actorId, null, action, outcome, operationId, studyRef, requestId, attemptId, visitId,
                patientId, correlationId, safeMetadata);
    }

    @Transactional
    public void recordService(String serviceIdentity, String action, String outcome, UUID operationId,
            String studyRef, UUID requestId, UUID attemptId, UUID visitId, UUID patientId,
            String correlationId, Map<String, Object> safeMetadata) {
        record(null, serviceIdentity, action, outcome, operationId, studyRef, requestId, attemptId, visitId,
                patientId, correlationId, safeMetadata);
    }

    private void record(UUID actorId, String serviceIdentity, String action, String outcome, UUID operationId,
            String studyRef, UUID requestId, UUID attemptId, UUID visitId, UUID patientId,
            String correlationId, Map<String, Object> metadata) {
        Map<String, Object> safe = validateSafeMetadata(metadata);
        put(safe, "studyRef", validateStudyRef(studyRef));
        put(safe, "operationId", operationId);
        put(safe, "requestId", requestId);
        put(safe, "attemptId", attemptId);
        put(safe, "visitId", visitId);
        put(safe, "patientId", patientId);
        put(safe, "correlationRef", validateCorrelationId(correlationId));
        put(safe, "serviceIdentity", serviceIdentity == null ? null : requireCode(serviceIdentity));
        auditWriter.record(AuditEntry.builder("imaging_audit_events", "IMAGING", requireCode(action), "IMAGING_STUDY")
                .entityId(null).recordLabel(null).visibleInActivityLog(false)
                .actorAccountId(actorId).actorType(actorId == null ? "SYSTEM" : "ACCOUNT")
                .metadata(safe).outcome(requireCode(outcome)).occurredAt(Instant.now()).build());
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null) values.put(key, value instanceof UUID uuid ? uuid.toString() : value);
    }

    private static String validateCorrelationId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid safe imaging audit correlation identifier");
        }
        return value;
    }

    private static String requireCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid imaging audit code");
        }
        return value;
    }

    private static String validateStudyRef(String value) {
        if (value != null && !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid opaque imaging study reference");
        }
        return value;
    }

    private static Map<String, Object> validateSafeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return new HashMap<>();
        if (metadata.size() > 12 || !SAFE_METADATA_KEYS.containsAll(metadata.keySet())) {
            throw new IllegalArgumentException("Imaging audit metadata contains an unapproved field");
        }
        Map<String, Object> safe = new HashMap<>();
        for (var entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                if (text.length() > 128 || !text.matches("[A-Za-z0-9._:+-]{1,128}")) {
                    throw new IllegalArgumentException("Imaging audit metadata contains an unsafe value");
                }
            } else if (value instanceof Number number) {
                if (number.longValue() < 0) {
                    throw new IllegalArgumentException("Imaging audit metadata contains a negative count");
                }
            } else if (value instanceof List<?> values) {
                if (!"evidenceCodes".equals(entry.getKey()) || values.size() > 32
                        || values.stream().anyMatch(item -> !(item instanceof String code)
                        || !code.matches("[A-Z][A-Z0-9_]{0,63}"))) {
                    throw new IllegalArgumentException("Imaging audit metadata contains an unsafe list");
                }
                value = List.copyOf(values);
            } else {
                throw new IllegalArgumentException("Imaging audit metadata contains an unsupported value");
            }
            safe.put(entry.getKey(), value);
        }
        return safe;
    }
}
