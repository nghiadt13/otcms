package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.UUID;

/** Clinical facts only; consumers establish Visit/Doctor responsibility separately. */
public interface ClinicalFollowUpEvidenceQueryService {
    FollowUpEvidence getEvidence(UUID visitId);
    record FollowUpEvidence(UUID visitId, boolean examinationExists, boolean examinationCompleted, boolean followUpIndicated) { }
}
