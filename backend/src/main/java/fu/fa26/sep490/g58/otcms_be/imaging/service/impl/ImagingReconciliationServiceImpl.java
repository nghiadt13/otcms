package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationQueue;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReconciliationCaseRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReleaseInboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationQuery;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;

/** Opens one durable, safe operator case per unresolved workflow source/category. */
@Service
public class ImagingReconciliationServiceImpl implements ImagingReconciliationService {
    private final ImagingReconciliationCaseRepository cases;
    private final ImagingAuditPersistenceService audit;
    private final ImagingIngestOperationRepository operations;
    private final ImagingReleaseInboxRepository releases;

    public ImagingReconciliationServiceImpl(ImagingReconciliationCaseRepository cases,
            ImagingAuditPersistenceService audit, ImagingIngestOperationRepository operations,
            ImagingReleaseInboxRepository releases) {
        this.cases = cases;
        this.audit = audit;
        this.operations = operations;
        this.releases = releases;
    }

    @Override
    @Transactional
    public void openOperation(UUID operationId, String category, String safeReason) {
        openOperation(operationId, category, classify(safeReason), safeReason);
    }

    @Override
    @Transactional
    public void openOperation(UUID operationId, String category,
            ImagingReconciliationClassification classification, String safeReason) {
        if (operationId == null) throw new IllegalArgumentException("operationId is required");
        insert(operationId, null, category, classification, null, safeReason);
    }

    @Override
    @Transactional
    public void openEvent(UUID eventId, String category, String safeReason) {
        openEvent(eventId, category, classify(safeReason), null, safeReason);
    }

    @Override
    @Transactional
    public void openEvent(UUID eventId, String category,
            ImagingReconciliationClassification classification, String safeReason) {
        openEvent(eventId, category, classification, null, safeReason);
    }

    @Override
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

    @Override
    @Transactional
    public Page<ImagingReconciliationQueue> listQueue(UUID actorId, ImagingReconciliationQuery query,
            String correlationId) {
        var pageable = PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.ASC, "openedAt"));
        var result = cases.findQueue(query.state(), query.classification(), query.category(), pageable)
                .map(ImagingReconciliationQueue::from);
        audit.recordActor(actorId, "IMAGING_RECONCILIATION_QUEUE_READ", "ALLOWED", null, null,
                null, null, null, null, correlationId,
                java.util.Map.of("resourceType", "IMAGING_RECONCILIATION_QUEUE",
                        "resultCount", result.getNumberOfElements()));
        return result;
    }

    @Override
    @Transactional(noRollbackFor = ImagingOperationException.class)
    public void recordDecision(UUID actorId, UUID caseId, ImagingReconciliationDecisionCommand command,
            String correlationId) {
        if (actorId == null || command == null || command.decision() == null
                || command.resolutionRef() == null
                || !command.resolutionRef().matches("INC-[0-9]{1,12}")) {
            throw new IllegalArgumentException("A decision and incident reference are required");
        }
        var item = cases.findById(caseId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        if (!"IMAGING_OPERATIONS".equals(item.getAssignedOwnerQueue())) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        if ("RESOLVED".equals(item.getState()) || "IN_PROGRESS".equals(item.getState())) {
            if (command.decision().name().equals(item.getResolutionDecision())
                    && command.resolutionRef().equals(item.getResolutionRef())) return;
            if ("RESOLVED".equals(item.getState())) {
                throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
            }
        }
        if (command.decision() == ImagingReconciliationDecision.KEEP_QUARANTINED
                && !ImagingReconciliationClassification.CONFLICT.name().equals(item.getClassification())
                && !ImagingReconciliationClassification.ORPHANED.name().equals(item.getClassification())) {
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        boolean closes = command.decision() != ImagingReconciliationDecision.START_REVIEW;
        Instant decidedAt = Instant.now();
        int changed = cases.recordAdminDecision(caseId, item.getState(), closes ? "RESOLVED" : "IN_PROGRESS",
                command.decision().name(), command.resolutionRef(), closes ? decidedAt : null, decidedAt,
                item.getUpdatedAt());
        if (changed != 1) throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        var auditMetadata = new java.util.HashMap<String, Object>();
        auditMetadata.put("decisionCode", command.decision().name());
        auditMetadata.put("resourceType", "IMAGING_RECONCILIATION_CASE");
        auditMetadata.put("caseId", item.getId().toString());
        if (item.getEventId() != null) auditMetadata.put("eventId", item.getEventId().toString());
        audit.recordActor(actorId, "IMAGING_RECONCILIATION_DECISION", command.decision().name(),
                item.getOperationId(), null, null, null, null, null, correlationId,
                auditMetadata);
    }

    @Override
    @Transactional
    public UUID reconcileOpenBatch(UUID afterCaseId) {
        UUID cursor = afterCaseId == null ? new UUID(0L, 0L) : afterCaseId;
        var batch = cases.findAllByStateAndAssignedOwnerQueueAndIdGreaterThanOrderByIdAsc(
                "OPEN", "IMAGING_OPERATIONS", cursor, PageRequest.of(0, 50));
        for (var item : batch) {
            if (!hasLinkedEvidence(item)) continue;
            Instant now = Instant.now();
            int updated = cases.resolveRetryableWithLinkedEvidence(item.getId(), item.getUpdatedAt(), now);
            if (updated == 1) {
                var metadata = new java.util.HashMap<String, Object>();
                metadata.put("resourceType", "IMAGING_RECONCILIATION_CASE");
                metadata.put("caseId", item.getId().toString());
                if (item.getEventId() != null) metadata.put("eventId", item.getEventId().toString());
                String correlationId = item.getEventId() != null
                        ? item.getEventId().toString() : item.getOperationId().toString();
                audit.recordService("FEAT_004", "IMAGING_RECONCILIATION_AUTO_CLOSE", "LINKED",
                        item.getOperationId(), null, null, null, null, null, correlationId, metadata);
            }
        }
        return batch.isEmpty() ? null : batch.getLast().getId();
    }

    private boolean hasLinkedEvidence(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity item) {
        if (!"RETRYABLE".equals(item.getClassification())) return false;
        if (item.getOperationId() != null) {
            return operations.findById(item.getOperationId())
                    .map(operation -> operation.getState()
                            == fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.LINKED)
                    .orElse(false);
        }
        if (item.getEventId() == null || !"RELEASE".equals(item.getCategory())
                || item.getSafeEvidenceSha256() == null) return false;
        return releases.findByEventId(item.getEventId())
                .filter(release -> "ACCEPTED".equals(release.getOutcome()))
                .map(release -> item.getSafeEvidenceSha256().trim().equals(release.getPayloadSha256().trim()))
                .orElse(false);
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
