package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.UUID;

/** Clinical-owned facts for UC28 composition, not authorization to close a visit. */
public interface ClinicalVisitCompletionEvidenceService {
    /**
     * Reads all Clinical orders across all rounds. This evidence does not prove that a Visit exists.
     * Null visitId is rejected with MSG-39; absence of Clinical records is valid empty evidence.
     * The caller owns Visit authorization and coordination with concurrent clinical changes.
     */
    ClinicalCompletionEvidence getCompletionEvidence(UUID visitId);

    /**
     * Active means non-cancelled, including orders whose results are already available.
     * Zero active orders means allNonCancelledOrdersHaveResults is true.
     * Imaging IDs include both ordered and available requests, sorted by createdAt then UUID string.
     * Result availability alone does not establish completed/released findings. Unpaid orders remain outstanding,
     * including legacy result-bearing orders created before payment-mediated dispatch.
     */
    record ClinicalCompletionEvidence(
            UUID visitId,
            boolean examinationExists,
            boolean examinationCompleted,
            int activeOrderCount,
            int outstandingOrderCount,
            boolean allNonCancelledOrdersHaveResults,
            List<UUID> imagingOrderIds) {
        public ClinicalCompletionEvidence {
            imagingOrderIds = List.copyOf(imagingOrderIds);
        }
    }
}
