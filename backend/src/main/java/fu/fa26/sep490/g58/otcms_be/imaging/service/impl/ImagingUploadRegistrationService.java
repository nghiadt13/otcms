package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Atomically reserves an imaging_studies row and its scoped upload fingerprint. */
@Service
public class ImagingUploadRegistrationService {
    private static final String UPLOAD_SCOPE = "uk_imaging_studies_upload_scope";
    private final ImagingStudyRepository studies;

    public ImagingUploadRegistrationService(ImagingStudyRepository studies) {
        this.studies = studies;
    }

    public Registration register(UUID actorId, UUID requestId, UUID attemptId, String key,
            String sha256, long bodySize, ImagingAssignedWork work) {
        Instant now = Instant.now();
        UUID studyId = UUID.randomUUID();
        ImagingOperationRecord operation = new ImagingOperationRecord(studyId, null, requestId,
                attemptId, work.visitId(), work.patientId(), actorId, ImagingOperationType.UPLOAD,
                ImagingOperationState.PROCESSING, sha256, bodySize, null, null, 0, 0,
                ImagingVerificationStatus.NOT_EVALUATED, List.of(), null, null, 0, now, now);
        try {
            ImagingStudyJpaEntity inserted = studies.saveAndFlush(new ImagingStudyJpaEntity(operation, key));
            return new Registration(inserted.toModel(), true);
        } catch (DataIntegrityViolationException failure) {
            if (!hasConstraint(failure, UPLOAD_SCOPE)) throw failure;
            ImagingStudyJpaEntity winner = studies
                    .findByActorAccountIdAndRequestIdAndAttemptIdAndUploadIdempotencyKey(
                            actorId, requestId, attemptId, key)
                    .orElseThrow(() -> failure);
            return replay(winner, sha256);
        }
    }

    private Registration replay(ImagingStudyJpaEntity study, String sha256) {
        ImagingOperationRecord record = study.toModel();
        if (!record.payloadSha256().equals(sha256)) {
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return new Registration(record, false);
    }

    private static boolean hasConstraint(Throwable failure, String expected) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && expected.equals(violation.getConstraintName())) return true;
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if ("23505".equals(next.getSQLState()) && next.getMessage() != null
                            && next.getMessage().contains(expected)) return true;
                }
            }
        }
        return false;
    }

    public record Registration(ImagingOperationRecord operation, boolean created) { }
}
