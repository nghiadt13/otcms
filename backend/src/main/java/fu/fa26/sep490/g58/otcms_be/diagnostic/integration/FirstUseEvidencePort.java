package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceReceipt;

/** Consumes immutable, owner-authenticated evidence; upload and preparation are not service use. */
public interface FirstUseEvidencePort {
    UseEvidenceReceipt recordFirstUseEvidence(UseEvidence evidence);
}
