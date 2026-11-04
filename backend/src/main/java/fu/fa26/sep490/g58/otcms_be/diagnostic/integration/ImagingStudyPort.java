package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import java.util.UUID;

/** FEAT-004 study identity and scoped view authorization are consumed through this port. */
public interface ImagingStudyPort {
    StudyEvidence confirmIngestedStudy(UUID eventId, String studyRef, UUID requestId, UUID visitId,
            UUID patientId, UUID attemptId);

    ViewAuthorization authorizeStudyView(String studyRef, UUID actorId, String purpose);

    ReleaseAcknowledgement acceptFindingsRelease(FindingsReleaseEvent event);
}
