package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.UUID;

public interface ClinicalImagingRequestService {
    SubmissionResult submit(ImagingRequestCommand command);

    record ImagingRequestCommand(UUID visitId, UUID doctorProfileId, String idempotencyKey,
            List<ImagingOrderRequest> orders) { }

    record ImagingOrderRequest(UUID serviceId, String clinicalReason, String bodyArea, String instructions) { }

    record SubmissionResult(ClinicalOrderingService.RoundView round, boolean replayed) { }
}
