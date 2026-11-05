package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PastMedicalRecordsDtos.HistoryResponse;
import java.util.UUID;

public interface ClinicalPastMedicalRecordsService {
    HistoryResponse getPastMedicalRecords(UUID currentVisitId, UUID doctorProfileId, UUID doctorAccountId, int page, int size);
}
