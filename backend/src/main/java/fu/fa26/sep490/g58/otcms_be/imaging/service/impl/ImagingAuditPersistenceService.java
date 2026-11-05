package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingAuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes PHI-safe audit evidence; callers must not swallow persistence failures on protected changes. */
@Service
public class ImagingAuditPersistenceService {
    private static final Set<String> SAFE_METADATA_KEYS = Set.of("purpose", "expiresAt", "state",
            "payloadSizeBytes", "instanceCount", "evidenceCodes", "eventId", "payloadSha256",
            "reasonCode", "resourceType", "decisionCode", "resultCount", "caseId");
    private final ImagingAuditEventRepository audits;

    public ImagingAuditPersistenceService(ImagingAuditEventRepository audits) { this.audits = audits; }

    @Transactional
    public void recordActor(UUID actorId, String action, String outcome, UUID operationId, String studyRef,
            UUID requestId, UUID attemptId, UUID visitId, UUID patientId, String correlationId,
            Map<String, Object> safeMetadata) {
        audits.saveAndFlush(new ImagingAuditEventJpaEntity(UUID.randomUUID(), actorId, null, requireCode(action),
                requireCode(outcome), operationId, validateStudyRef(studyRef), requestId, attemptId, visitId, patientId,
                validateCorrelationId(correlationId), validateSafeMetadata(safeMetadata), Instant.now()));
    }

    @Transactional
    public void recordService(String serviceIdentity, String action, String outcome, UUID operationId,
            String studyRef, UUID requestId, UUID attemptId, UUID visitId, UUID patientId,
            String correlationId, Map<String, Object> safeMetadata) {
        audits.saveAndFlush(new ImagingAuditEventJpaEntity(UUID.randomUUID(), null, requireCode(serviceIdentity),
                requireCode(action), requireCode(outcome), operationId, validateStudyRef(studyRef),
                requestId, attemptId, visitId, patientId,
                validateCorrelationId(correlationId), validateSafeMetadata(safeMetadata), Instant.now()));
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
        if (metadata == null || metadata.isEmpty()) return Map.of();
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
        return Map.copyOf(safe);
    }
}
