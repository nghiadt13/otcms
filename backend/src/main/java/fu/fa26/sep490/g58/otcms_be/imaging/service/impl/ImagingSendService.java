package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
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

/** Records confirmation dispatch state and its idempotency key on the owning imaging study. */
@Service
public class ImagingSendService {
    private final ImagingStudyRepository studies;
    private final ImagingAuditPersistenceService audit;

    public ImagingSendService(ImagingStudyRepository studies, ImagingAuditPersistenceService audit) {
        this.studies = studies;
        this.audit = audit;
    }

    @Transactional
    public ImagingOperationStatus send(UUID actorId, UUID operationId, String key) {
        ImagingStudyJpaEntity study = studies.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        String fingerprint = hash(operationId.toString());
        if (study.getSendIdempotencyKey() != null) {
            if (!study.getSendIdempotencyKey().equals(key)
                    || !fingerprint.equals(trim(study.getSendFingerprint()))) {
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
        study.prepareConfirmation(eventId, key, fingerprint, now);
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

    private static String trim(String value) { return value == null ? null : value.trim(); }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
