package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedImagingWork;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import java.util.UUID;

/** FEAT-003 request operations; business rules and record access stay behind this interface. */
public interface DiagnosticRequestService {
    DiagnosticRequestView startImaging(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String correlationId);
    /** Retired direct creation boundary. Always denies; use Reception paid Clinical round dispatch. */
    DiagnosticRequestView create(DiagnosticActor actor, UUID visitId, String idempotencyKey,
            UUID serviceId, String imagingType, String bodyArea, String clinicalReason,
            UUID roomId, UUID serviceOrderId, String correlationId);

    DiagnosticRequestPage listVisitRequests(DiagnosticActor actor, UUID visitId, int page, int size);

    DiagnosticRequestView get(DiagnosticActor actor, UUID requestId);

    DiagnosticRequestPage listAssignedUploadWork(DiagnosticActor actor, UUID roomId, int page, int size);

    DiagnosticAssignedImagingWork requireAssignedImagingWork(DiagnosticActor actor, UUID requestId, UUID attemptId);

    DiagnosticRequestView cancel(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, CancellationCause cause, String reason, String correlationId);

    DiagnosticRequestView transferRoom(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, UUID newRoomId, String reason, String correlationId);
}
