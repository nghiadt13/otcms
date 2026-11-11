package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Patient-facing visit history and result projections. */
public final class PatientVisitResultDtos {
    private PatientVisitResultDtos() { }

    public record VisitResultPage(List<VisitSummary> items, int page, int size, long totalItems, int totalPages) {
        public VisitResultPage { items = List.copyOf(items); }
        @Override public String toString() { return "VisitResultPage[redacted]"; }
    }

    public record VisitSummary(UUID visitId, LocalDate visitDate, String status, String serviceName,
            PatientRef patient) {
        @Override public String toString() { return "VisitSummary[redacted]"; }
    }
    public record PatientRef(UUID id, String fullName, String relationship) {
        @Override public String toString() { return "PatientRef[redacted]"; }
    }
    public record Icd10Ref(String code, String title) { }
    public record ExaminationSection(String reason, String symptoms, String findings,
            List<Icd10Ref> primaryDiagnoses, List<Icd10Ref> secondaryDiagnoses,
            String treatmentDirection, String advice, LocalDate followUpDate, String followUpNote) {
        public ExaminationSection {
            primaryDiagnoses = List.copyOf(primaryDiagnoses);
            secondaryDiagnoses = List.copyOf(secondaryDiagnoses);
        }
        @Override public String toString() { return "ExaminationSection[redacted]"; }
    }
    public record ImagingItem(String studyId, String serviceName, String finding) {
        @Override public String toString() { return "ImagingItem[redacted]"; }
    }
    public record PrescriptionRef(UUID id, Instant issuedAt, int itemCount) {
        @Override public String toString() { return "PrescriptionRef[redacted]"; }
    }
    public record AdministrativeInfo(String fullName, LocalDate dateOfBirth, String sex,
            String patientCode, String phone, String address) {
        @Override public String toString() { return "AdministrativeInfo[redacted]"; }
    }
    public record ResultSheet(UUID id, long version, String status, Instant issuedAt,
            String title, String fileUrl) {
        @Override public String toString() { return "ResultSheet[redacted]"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VisitResult(UUID visitId, LocalDate visitDate, String status, PatientRef patient,
            UUID previousVisitId, UUID nextVisitId, List<String> visibleBlocks,
            ExaminationSection examination, List<ImagingItem> imaging,
            List<PrescriptionRef> prescriptions, AdministrativeInfo administrative,
            List<ResultSheet> resultSheets) {
        public VisitResult {
            visibleBlocks = List.copyOf(visibleBlocks);
            if (imaging != null) imaging = List.copyOf(imaging);
            if (prescriptions != null) prescriptions = List.copyOf(prescriptions);
            if (resultSheets != null) resultSheets = List.copyOf(resultSheets);
        }
        @Override public String toString() { return "VisitResult[redacted]"; }
    }
}
