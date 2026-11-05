package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService.DocumentSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService.IssuedPrescriptionSummary;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import java.time.Instant;
import java.util.*;

public final class PastMedicalRecordsDtos {
    private PastMedicalRecordsDtos() { }
    public record HistoryResponse(UUID currentVisitId, UUID patientProfileId, List<HistoricalVisitRecord> items,
            int page, int size, long total) {
        public HistoryResponse { items = List.copyOf(items); }
        @Override public String toString() { return "HistoryResponse[redacted]"; }
    }
    public record HistoricalVisitRecord(UUID visitId, Instant arrivedAt, Examination examination, Advice advice,
            IssuedPrescriptionSummary prescription, List<DocumentSummary> documents, List<DiagnosticHistoryItem> imagingResults) {
        public HistoricalVisitRecord { documents = List.copyOf(documents); imagingResults = List.copyOf(imagingResults); }
        @Override public String toString() { return "HistoricalVisitRecord[redacted]"; }
    }
}
