package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;

/** Internal FEAT-003 application boundary for trusted owner evidence. */
public interface DiagnosticEvidenceService {
    DiagnosticRequestView recordFirstUse(DiagnosticActor actor, UseEvidence evidence, String correlationId);

    DiagnosticRequestView linkConfirmedStudy(DiagnosticActor actor, StudyEvidence evidence,
            String correlationId);

}
