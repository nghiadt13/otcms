package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PastMedicalRecordsDtos.HistoryResponse;
import java.util.UUID;
import java.time.LocalDate;

public interface ClinicalPastMedicalRecordsService {
    HistoryResponse getPastMedicalRecords(UUID currentVisitId, UUID doctorProfileId, UUID doctorAccountId, int page, int size);
    default HistoryResponse getPastMedicalRecords(UUID currentVisitId, UUID doctorProfileId, UUID doctorAccountId,
            int page, int size, LocalDate date) {
        return getPastMedicalRecords(currentVisitId, doctorProfileId, doctorAccountId, page, size);
    }
}
