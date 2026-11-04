package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.MedicalHistoryDtos.*;
import java.util.UUID;

public interface PatientMedicalHistoryService {
    MedicalHistoryResponse get(AuthenticatedAccount actor, UUID patientProfileId);
    MedicalHistoryResponse update(AuthenticatedAccount actor, UUID patientProfileId, UpdateMedicalHistoryRequest request);
}
