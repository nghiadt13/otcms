package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReconciliationCaseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens one durable, safe operator case per unresolved workflow source/category. */
@Service
public class ImagingReconciliationService {
    private final ImagingReconciliationCaseRepository cases;

    public ImagingReconciliationService(ImagingReconciliationCaseRepository cases) { this.cases = cases; }

    @Transactional
    public void openOperation(UUID operationId, String category, String safeReason) {
        openOperation(operationId, category, classify(safeReason), safeReason);
    }

    @Transactional
    public void openOperation(UUID operationId, String category,
            ImagingReconciliationClassification classification, String safeReason) {
        if (operationId == null) throw new IllegalArgumentException("operationId is required");
        insert(operationId, null, category, classification, null, safeReason);
    }

    @Transactional
    public void openEvent(UUID eventId, String category, String safeReason) {
        openEvent(eventId, category, classify(safeReason), null, safeReason);
    }

    @Transactional
    public void openEvent(UUID eventId, String category,
            ImagingReconciliationClassification classification, String safeReason) {
        openEvent(eventId, category, classification, null, safeReason);
    }

    @Transactional
    public void openEvent(UUID eventId, String category,
            ImagingReconciliationClassification classification, String safeEvidenceSha256, String safeReason) {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        insert(null, eventId, category, classification, safeEvidenceSha256, safeReason);
    }

    private void insert(UUID operationId, UUID eventId, String category,
            ImagingReconciliationClassification classification, String safeEvidenceSha256, String safeReason) {
        if (category == null || !category.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Invalid reconciliation category");
        }
        if (classification == null) throw new IllegalArgumentException("classification is required");
        if (safeReason == null || !safeReason.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid safe reconciliation reason");
        }
        if (safeEvidenceSha256 != null && !safeEvidenceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid safe reconciliation fingerprint");
        }
        cases.insertOpenCaseIfAbsent(UUID.randomUUID(), operationId, eventId, category,
                classification.name(), safeReason, safeEvidenceSha256, "IMAGING_OPERATIONS");
    }

    static ImagingReconciliationClassification classify(String safeReason) {
        if (safeReason == null) return ImagingReconciliationClassification.ORPHANED;
        return switch (safeReason) {
            case "IDENTITY_MISMATCH", "DUPLICATE_INSTANCE_UID", "UID_ALREADY_CLAIMED",
                    "RELEASE_OWNER_MISMATCH", "RELEASE_EVENT_CONFLICT", "EVIDENCE_MISMATCH",
                    "CONFIRMATION_REJECTED" ->
                ImagingReconciliationClassification.CONFLICT;
            case "RELEASE_BEFORE_LINKED", "CONFIRMATION_UNAVAILABLE", "ARCHIVE_UNAVAILABLE",
                    "UPLOAD_AUDIT_UNAVAILABLE", "UPLOAD_OUTCOME_UNCERTAIN",
                    "ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN", "STATE_TRANSITION_FAILED",
                    "CONFIRMATION_RETRY_EXHAUSTED" ->
                ImagingReconciliationClassification.RETRYABLE;
            case "IDENTITY_UNKNOWN", "RELEASE_STUDY_NOT_FOUND", "RELEASE_EVENT_INVALID",
                    "RELEASE_INGEST_OPERATION_MISSING" ->
                ImagingReconciliationClassification.ORPHANED;
            default -> throw new IllegalArgumentException("Unclassified reconciliation reason code");
        };
    }
}
