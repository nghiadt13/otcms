package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRequestService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Calls FEAT-003's public service contract; no diagnostic repository or entity crosses the boundary. */
@Component
public class DiagnosticImagingWorkAuthorizationAdapter implements ImagingWorkAuthorizationPort {
    private final DiagnosticRequestService requests;
    private final DiagnosticStatusService statuses;

    public DiagnosticImagingWorkAuthorizationAdapter(DiagnosticRequestService requests, DiagnosticStatusService statuses) {
        this.requests = requests;
        this.statuses = statuses;
    }

    @Override
    public void requireStatusRead(UUID actorAccountId, UUID requestId) {
        try {
            statuses.getStatus(DiagnosticActor.user(actorAccountId, AccountRole.IMAGING_TECHNICIAN), requestId);
        } catch (DiagnosticOperationException exception) {
            throw new ImagingOperationException(exception.outcome() == DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE
                    ? ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE : ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    @Override
    public ImagingAssignedWork requireAssignedImagingWork(UUID actorAccountId, UUID requestId, UUID attemptId) {
        try {
            var decision = requests.requireAssignedImagingWork(
                    DiagnosticActor.user(actorAccountId, AccountRole.IMAGING_TECHNICIAN), requestId, attemptId);
            return new ImagingAssignedWork(actorAccountId, decision.requestId(), decision.attemptId(),
                    decision.visitId(), decision.patientId(), decision.assignmentVersion(), decision.currentlyAssigned());
        } catch (DiagnosticOperationException exception) {
            DiagnosticOperationOutcome outcome = exception.outcome();
            if (outcome == DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE) {
                throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
            }
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }
}
