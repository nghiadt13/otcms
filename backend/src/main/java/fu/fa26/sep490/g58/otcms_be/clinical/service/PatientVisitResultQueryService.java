package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.VisitResult;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.VisitResultPage;
import java.util.UUID;

/** Patient-owned composition boundary for own and confirmed dependent visit results. */
public interface PatientVisitResultQueryService {
    VisitResultPage list(UUID accountId, UUID patientProfileId, int page, int size);
    VisitResult get(UUID accountId, UUID visitId);
}
