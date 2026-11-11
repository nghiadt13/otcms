package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ArchiveInstanceReceipt;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ImagingArchivePort;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingUploadCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ImagingDicomStudyValidator;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ValidatedDicomStudy;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

/** Owns body fingerprinting, idempotent operation creation, validation and private archive ingestion. */
@Service
public class ImagingUploadOrchestrator {
    private final ImagingProperties settings;
    private final ImagingRequestBodySpooler spooler;
    private final ImagingUploadRegistrationService registration;
    private final ImagingDicomStudyValidator validator;
    private final ImagingArchivePort archive;
    private final ImagingIngestOperationPersistenceService operations;
    private final ImagingStudyPersistenceService studies;
    private final ImagingAuditPersistenceService audit;
    private final ImagingReconciliationService reconciliation;
    private final Semaphore activeUploads;

    public ImagingUploadOrchestrator(ImagingProperties settings, ImagingRequestBodySpooler spooler,
            ImagingUploadRegistrationService registration, ImagingDicomStudyValidator validator,
            ImagingArchivePort archive, ImagingIngestOperationPersistenceService operations,
            ImagingStudyPersistenceService studies, ImagingAuditPersistenceService audit,
            ImagingReconciliationService reconciliation) {
        this.settings = settings;
        this.spooler = spooler;
        this.registration = registration;
        this.validator = validator;
        this.archive = archive;
        this.operations = operations;
        this.studies = studies;
        this.audit = audit;
        this.reconciliation = reconciliation;
        this.activeUploads = new Semaphore(Math.max(1, settings.getMaxConcurrentUploads()));
    }

    public ImagingOperationStatus upload(ImagingUploadCommand command, ImagingAssignedWork work) {
        if (!settings.isEnabled() || settings.getMaxUploadBytes() < 1
                || settings.getMaxUploadFiles() < 1 || settings.getMaxConcurrentUploads() < 1) {
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (!activeUploads.tryAcquire()) {
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        try (ImagingRequestBodySpooler.Spool body = spooler.spool(command.content(),
                command.contentLength(), settings.getMaxUploadBytes())) {
            ImagingUploadRegistrationService.Registration registered;
            try {
                registered = registration.register(command.actorAccountId(), command.requestId(), command.attemptId(),
                        command.idempotencyKey(), body.sha256(), body.size(), work);
            } catch (ImagingOperationException concurrentReplay) {
                if (concurrentReplay.outcome() != ImagingOperationOutcome.IDEMPOTENCY_CONFLICT) throw concurrentReplay;
                // Re-read after the conflicting insert transaction has rolled back; the winner is now durable.
                try {
                    registered = registration.register(command.actorAccountId(), command.requestId(), command.attemptId(),
                            command.idempotencyKey(), body.sha256(), body.size(), work);
                } catch (ImagingOperationException replayConflict) {
                    if (replayConflict.outcome() == ImagingOperationOutcome.IDEMPOTENCY_CONFLICT) {
                        audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "IDEMPOTENCY_CONFLICT", null,
                                null, command.requestId(), command.attemptId(), work.visitId(), work.patientId(),
                                command.requestId().toString(), java.util.Map.of("reasonCode", "UPLOAD_IDEMPOTENCY_CONFLICT"));
                    }
                    throw replayConflict;
                }
            }
            if (!registered.created()) {
                var replay = registered.operation();
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "IDEMPOTENT_REPLAY", replay.id(),
                        replay.studyRef(), replay.requestId(), replay.attemptId(), replay.visitId(), replay.patientId(),
                        replay.id().toString(), java.util.Map.of("payloadSizeBytes", body.size()));
                return replay.toStatus();
            }
            try {
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "STARTED", registered.operation().id(),
                        null, command.requestId(), command.attemptId(), work.visitId(), work.patientId(),
                        registered.operation().id().toString(), java.util.Map.of("payloadSizeBytes", body.size()));
            } catch (RuntimeException auditFailure) {
                safeTransition(registered.operation(), ImagingOperationState.RECONCILIATION_REQUIRED,
                        "UPLOAD_AUDIT_UNAVAILABLE");
                reconciliation.openOperation(registered.operation().id(), "INGEST", "UPLOAD_AUDIT_UNAVAILABLE");
                return currentStatus(registered.operation().id());
            }
            return ingest(command, work, body, registered.operation());
        } finally {
            activeUploads.release();
        }
    }

    private ImagingOperationStatus ingest(ImagingUploadCommand command, ImagingAssignedWork work,
            ImagingRequestBodySpooler.Spool body, ImagingOperationRecord operation) {
        try (InputStream input = java.nio.file.Files.newInputStream(body.path());
                ValidatedDicomStudy validated = validator.validateAndExtract(command.contentType(), body.size(), input, work)) {
            var summary = validated.summary();
            operations.recordValidation(operation.id(), summary);
            List<ArchiveInstanceReceipt> receipts = new ArrayList<>();
            try {
                for (var file : validated.instanceFiles()) receipts.add(archive.storeInstance(file));
                ImagingStudyPersistenceService.StudyReferences references = studies.persistConfirmedStudy(
                        operation, summary, validated.objects(), receipts);
                operations.recordStudyReferences(operation.id(), references.studyRef(), references.archiveStudyRef());
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "ARCHIVED_NOT_SENT", operation.id(),
                        references.studyRef(), operation.requestId(), operation.attemptId(), operation.visitId(),
                        operation.patientId(), operation.id().toString(),
                        java.util.Map.of("instanceCount", summary.instanceCount(),
                                "evidenceCodes", summary.evidenceCodes()));
                operations.transition(operation.id(), operation.version(), ImagingOperationState.ARCHIVED_NOT_SENT,
                        null, Instant.now());
                return currentStatus(operation.id());
            } catch (ImagingOperationException exception) {
                if (exception.outcome() != ImagingOperationOutcome.ARCHIVE_STUDY_ALREADY_LINKED) throw exception;
                String archiveStudyRef = receipts.isEmpty() ? null : receipts.getFirst().archiveStudyRef();
                operations.recordStudyReferences(operation.id(), null, archiveStudyRef);
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "REJECTED", operation.id(), null,
                        operation.requestId(), operation.attemptId(), operation.visitId(), operation.patientId(),
                        operation.id().toString(), java.util.Map.of("reasonCode", "ARCHIVE_STUDY_ALREADY_LINKED"));
                operations.transition(operation.id(), operation.version(), ImagingOperationState.FAILED,
                        "ARCHIVE_STUDY_ALREADY_LINKED", Instant.now());
                return currentStatus(operation.id());
            } catch (RuntimeException exception) {
                ImagingOperationState next = receipts.isEmpty()
                        && exception instanceof ImagingOperationException imaging
                        && imaging.outcome() == ImagingOperationOutcome.FILE_INVALID
                        ? ImagingOperationState.FAILED : ImagingOperationState.RECONCILIATION_REQUIRED;
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD",
                        next == ImagingOperationState.FAILED ? "FAILED" : "RECONCILIATION_REQUIRED",
                        operation.id(), operation.studyRef(), operation.requestId(), operation.attemptId(),
                        operation.visitId(), operation.patientId(), operation.id().toString(),
                        java.util.Map.of("reasonCode", next == ImagingOperationState.FAILED
                                ? "ARCHIVE_REJECTED" : "ARCHIVE_OUTCOME_UNCERTAIN"));
                safeTransition(operation, next, next == ImagingOperationState.FAILED
                        ? "ARCHIVE_REJECTED" : "ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN");
                if (next == ImagingOperationState.RECONCILIATION_REQUIRED) {
                    reconciliation.openOperation(operation.id(), "INGEST", "ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN");
                }
                if (next == ImagingOperationState.RECONCILIATION_REQUIRED) return currentStatus(operation.id());
                throw exception;
            }
        } catch (ImagingOperationException exception) {
            if (exception.outcome() == ImagingOperationOutcome.FILE_INVALID) {
                audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "REJECTED", operation.id(), null,
                        operation.requestId(), operation.attemptId(), operation.visitId(), operation.patientId(),
                        operation.id().toString(), java.util.Map.of("reasonCode", "UPLOAD_VALIDATION_FAILED"));
                safeTransition(operation, ImagingOperationState.FAILED, "UPLOAD_VALIDATION_FAILED");
            }
            throw exception;
        } catch (java.io.IOException exception) {
            audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "FAILED", operation.id(), null,
                    operation.requestId(), operation.attemptId(), operation.visitId(), operation.patientId(),
                    operation.id().toString(), java.util.Map.of("reasonCode", "UPLOAD_TEMPORARY_IO_FAILED"));
            safeTransition(operation, ImagingOperationState.FAILED, "UPLOAD_TEMPORARY_IO_FAILED");
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException exception) {
            audit.recordActor(command.actorAccountId(), "IMAGING_UPLOAD", "RECONCILIATION_REQUIRED", operation.id(),
                    operation.studyRef(), operation.requestId(), operation.attemptId(), operation.visitId(),
                    operation.patientId(), operation.id().toString(),
                    java.util.Map.of("reasonCode", "UPLOAD_OUTCOME_UNCERTAIN"));
            safeTransition(operation, ImagingOperationState.RECONCILIATION_REQUIRED, "UPLOAD_OUTCOME_UNCERTAIN");
            reconciliation.openOperation(operation.id(), "INGEST", "UPLOAD_OUTCOME_UNCERTAIN");
            return currentStatus(operation.id());
        }
    }

    private void safeTransition(ImagingOperationRecord operation, ImagingOperationState next, String code) {
        try { operations.transition(operation.id(), operation.version(), next, code, Instant.now()); }
        catch (RuntimeException ignored) {
            reconciliation.openOperation(operation.id(), "INGEST", "STATE_TRANSITION_FAILED");
        }
    }

    private ImagingOperationStatus currentStatus(UUID id) {
        return operations.findById(id).orElseThrow(() ->
                new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED)).toStatus();
    }
}
