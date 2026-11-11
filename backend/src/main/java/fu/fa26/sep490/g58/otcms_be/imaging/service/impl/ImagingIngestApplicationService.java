package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingIngestService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingUploadCommand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * T012 authenticated authorization boundary. Upload orchestration is completed in T016;
 * status and SEND lookups use durable operation ownership and repeat owner authorization.
 */
@Service
public class ImagingIngestApplicationService implements ImagingIngestService {
    private final ImagingAssignedWorkAuthorizationService workAuthorization;
    private final ImagingIngestOperationPersistenceService operations;
    private final ImagingUploadOrchestrator uploads;
    private final ImagingSendService sends;
    private final ImagingAuditPersistenceService audit;

    @Autowired
    public ImagingIngestApplicationService(ImagingAssignedWorkAuthorizationService workAuthorization,
            ImagingIngestOperationPersistenceService operations, ImagingUploadOrchestrator uploads,
            ImagingSendService sends, ImagingAuditPersistenceService audit) {
        this.workAuthorization = workAuthorization;
        this.operations = operations;
        this.uploads = uploads;
        this.sends = sends;
        this.audit = audit;
    }

    /** Test compatibility constructor for status/SEND authorization unit tests. */
    public ImagingIngestApplicationService(ImagingAssignedWorkAuthorizationService workAuthorization,
            ImagingIngestOperationPersistenceService operations) {
        this(workAuthorization, operations, null, null, null);
    }

    @Override
    public ImagingOperationStatus upload(ImagingUploadCommand command) {
        var work = workAuthorization.requireAssignedWork(
                command.actorAccountId(), command.requestId(), command.attemptId());
        if (uploads == null) throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        return uploads.upload(command, work);
    }

    @Override
    public ImagingOperationStatus getOperationStatus(UUID actorAccountId, UUID operationId) {
        var operation = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        if (operation.state() == ImagingOperationState.CONFIRMATION_PENDING
                || operation.state() == ImagingOperationState.LINKED) {
            // FEAT-003 can publish the result and close the active upload assignment before this
            // asynchronous confirmation reaches LINKED. The technician who created the operation
            // must still be able to poll its final status; this does not grant DICOM viewer access.
            if (!operation.actorAccountId().equals(actorAccountId)) {
                throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
            }
        } else {
            workAuthorization.requireAssignedWork(actorAccountId, operation.requestId(), operation.attemptId());
        }
        if (audit != null) {
            audit.recordActor(actorAccountId, "IMAGING_OPERATION_STATUS_READ", "ALLOWED", operationId,
                    operation.studyRef(), operation.requestId(), operation.attemptId(), operation.visitId(),
                    operation.patientId(), operationId.toString(), java.util.Map.of("state", operation.state().name()));
        }
        return operation.toStatus();
    }

    @Override
    public Optional<ImagingOperationStatus> getLatestOperation(UUID actorAccountId, UUID requestId, UUID attemptId) {
        workAuthorization.requireAssignedWork(actorAccountId, requestId, attemptId);
        Optional<ImagingOperationRecord> latest = operations.findLatest(requestId, attemptId);
        if (audit != null) {
            latest.ifPresent(operation -> audit.recordActor(actorAccountId, "IMAGING_OPERATION_STATUS_READ",
                    "ALLOWED", operation.id(), operation.studyRef(), operation.requestId(), operation.attemptId(),
                    operation.visitId(), operation.patientId(), operation.id().toString(),
                    java.util.Map.of("state", operation.state().name())));
        }
        return latest.map(ImagingOperationRecord::toStatus);
    }

    @Override
    public ImagingOperationStatus send(UUID actorAccountId, UUID operationId, String idempotencyKey) {
        var operation = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        workAuthorization.requireAssignedWork(actorAccountId, operation.requestId(), operation.attemptId());
        if (sends == null) {
            if (operation.state() != ImagingOperationState.ARCHIVED_NOT_SENT) {
                throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
            }
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        return sends.send(actorAccountId, operationId, idempotencyKey);
    }
}
