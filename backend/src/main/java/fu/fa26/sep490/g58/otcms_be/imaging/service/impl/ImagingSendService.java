package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIdempotencyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durably records a SEND request, stable event ID and idempotency fingerprint before dispatch. */
@Service
public class ImagingSendService {
    private final ImagingIngestOperationRepository operations;
    private final ImagingIdempotencyRepository idempotency;
    private final ImagingConfirmationOutboxRepository outbox;
    private final ImagingIngestOperationPersistenceService operationPersistence;
    private final ImagingAuditPersistenceService audit;

    public ImagingSendService(ImagingIngestOperationRepository operations,
            ImagingIdempotencyRepository idempotency, ImagingConfirmationOutboxRepository outbox,
            ImagingIngestOperationPersistenceService operationPersistence,
            ImagingAuditPersistenceService audit) {
        this.operations = operations;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.operationPersistence = operationPersistence;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = ImagingOperationException.class)
    public ImagingOperationStatus send(UUID actorId, UUID operationId, String key) {
        var operation = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        var record = operation.toModel();
        String digest = hash(operationId.toString());
        var previous = idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                actorId, record.requestId(), record.attemptId(), ImagingOperationType.SEND, key);
        if (previous.isPresent()) {
            if (!previous.get().toModel().payloadSha256().equals(digest)
                    || !previous.get().toModel().targetOperationId().equals(operationId)) {
                audit.recordActor(actorId, "IMAGING_SEND", "IDEMPOTENCY_CONFLICT", operationId,
                        record.studyRef(), record.requestId(), record.attemptId(), record.visitId(),
                        record.patientId(), operationId.toString(), Map.of("reasonCode", "SEND_IDEMPOTENCY_CONFLICT"));
                throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            var existingEvent = outbox.findByOperationId(operationId).orElse(null);
            String eventId = existingEvent == null ? operationId.toString() : existingEvent.getEventId().toString();
            audit.recordActor(actorId, "IMAGING_SEND", "IDEMPOTENT_REPLAY", operationId, record.studyRef(),
                    record.requestId(), record.attemptId(), record.visitId(), record.patientId(), eventId,
                    Map.of("eventId", eventId));
            return record.toStatus();
        }
        if (record.state() != ImagingOperationState.ARCHIVED_NOT_SENT
                || record.studyRef() == null
                || record.verificationStatus() != fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus.CONFIRMED_MATCH) {
            audit.recordActor(actorId, "IMAGING_SEND", "REJECTED", operationId, record.studyRef(),
                    record.requestId(), record.attemptId(), record.visitId(), record.patientId(),
                    operationId.toString(), Map.of("reasonCode", "SEND_STATE_INVALID"));
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        audit.recordActor(actorId, "IMAGING_SEND", "ACCEPTED", operationId, record.studyRef(),
                record.requestId(), record.attemptId(), record.visitId(), record.patientId(),
                eventId.toString(), Map.of("eventId", eventId.toString()));
        outbox.saveAndFlush(new ImagingConfirmationOutboxJpaEntity(UUID.randomUUID(), eventId, operationId,
                record.studyRef(), record.requestId(), record.attemptId(), record.visitId(), record.patientId(),
                record.verificationStatus().name(), Map.of("evidenceCodes", record.verificationEvidence()),
                digest, "PENDING", 0, now, null, null, null));
        idempotency.saveAndFlush(new ImagingIdempotencyRecordJpaEntity(new ImagingIdempotencyRecord(
                UUID.randomUUID(), actorId, record.requestId(), record.attemptId(), ImagingOperationType.SEND,
                key, digest, operationId, null, now)));
        operationPersistence.transition(operationId, record.version(), ImagingOperationState.CONFIRMATION_PENDING,
                null, now);
        return operationPersistence.findById(operationId).orElseThrow(() ->
                new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED)).toStatus();
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
