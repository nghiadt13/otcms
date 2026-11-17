package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.UUID;

/** Creates laboratory orders owned by the current doctor and their visit. */
public interface ClinicalLaboratoryRequestService {
    SubmissionResult submit(SubmitRequest request);

    record OrderRequest(UUID serviceId, String clinicalReason, String instructions) { }
    record SubmitRequest(UUID visitId, UUID doctorProfileId, String idempotencyKey,
            boolean patientConsentConfirmed, List<OrderRequest> orders) { }
    record SubmissionResult(ClinicalOrderingService.RoundView round, boolean replayed) { }
}
