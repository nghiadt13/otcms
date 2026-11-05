package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import java.time.Instant;
import java.util.*;

public interface ClinicalPrescriptionService {
    PrescriptionView getPrescription(UUID visitId, UUID doctorProfileId);
    PrescriptionView saveDraft(DraftCommand command);
    PrescriptionView issue(IssuanceCommand command);

    record MedicineItem(String medicine, String dose, String frequency, String usage) {
        @Override public String toString() { return "MedicineItem[redacted]"; }
    }
    record DraftCommand(UUID visitId, UUID doctorProfileId, Long version, List<MedicineItem> items) {
        public DraftCommand { items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items)); }
        @Override public String toString() { return "DraftCommand[redacted]"; }
    }
    record IssuanceCommand(UUID visitId, UUID doctorProfileId, Long version, String idempotencyKey) {
        @Override public String toString() { return "IssuanceCommand[redacted]"; }
    }
    record PrescriptionLine(int lineNumber, String medicine, String dose, String frequency, String usage) {
        @Override public String toString() { return "PrescriptionLine[redacted]"; }
    }
    record PrescriptionView(UUID id, UUID visitId, UUID patientProfileId, UUID doctorProfileId,
            ClinicalPrescriptionStatus status, Instant issuedAt, long version, List<PrescriptionLine> items) {
        public PrescriptionView { items = List.copyOf(items); }
        @Override public String toString() { return "PrescriptionView[redacted]"; }
    }
}
