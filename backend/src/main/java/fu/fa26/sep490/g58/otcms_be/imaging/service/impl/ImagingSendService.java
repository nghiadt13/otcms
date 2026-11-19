package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records confirmation dispatch state on the owning study; its SEND key lives in idempotency_records (owner = study id). */
@Service
public class ImagingSendService {
    private final ImagingStudyRepository studies;
    private final ImagingAuditPersistenceService audit;
    private final IdempotencyStore idempotency;

    public ImagingSendService(ImagingStudyRepository studies, ImagingAuditPersistenceService audit,
            IdempotencyStore idempotency) {
        this.studies = studies;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    @Transactional
    public ImagingOperationStatus send(UUID actorId, UUID operationId, String key) {
        ImagingStudyJpaEntity study = studies.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        String fingerprint = hash(operationId.toString());
        IdempotencyReplay replay = idempotency.find(IdempotencyScope.IMAGING_SEND, operationId, key).orElse(null);
        // A study has at most one SEND: once one was accepted, only its own key replays it and any other key conflicts.
        if (replay != null || study.getConfirmationEventId() != null) {
            if (replay == null || !replay.matches(fingerprint)) {
                audit.recordActor(actorId, "IMAGING_SEND", "IDEMPOTENCY_CONFLICT", operationId,
                        study.getStudyRef(), study.getRequestId(), study.getAttemptId(), study.getVisitId(),
                        study.getPatientId(), operationId.toString(), Map.of("reasonCode", "SEND_IDEMPOTENCY_CONFLICT"));
                throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            audit.recordActor(actorId, "IMAGING_SEND", "IDEMPOTENT_REPLAY", operationId, study.getStudyRef(),
                    study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                    study.getConfirmationEventId().toString(), Map.of("eventId", study.getConfirmationEventId().toString()));
            return study.toModel().toStatus();
        }
        if (study.getState() != ImagingOperationState.ARCHIVED_NOT_SENT || study.getStudyRef() == null
                || !"CONFIRMED_MATCH".equals(study.getVerificationStatus())) {
            audit.recordActor(actorId, "IMAGING_SEND", "REJECTED", operationId, study.getStudyRef(),
                    study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                    operationId.toString(), Map.of("reasonCode", "SEND_STATE_INVALID"));
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        // Claim the key before changing the study: a concurrent SEND that loses the claim is rejected as a conflict.
        if (!idempotency.record(IdempotencyScope.IMAGING_SEND, operationId, key, fingerprint, operationId)) {
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        study.prepareConfirmation(eventId, now);
        try {
            studies.saveAndFlush(study);
        } catch (DataIntegrityViolationException duplicateKey) {
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        audit.recordActor(actorId, "IMAGING_SEND", "ACCEPTED", operationId, study.getStudyRef(),
                study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                eventId.toString(), Map.of("eventId", eventId.toString()));
        return study.toModel().toStatus();
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
