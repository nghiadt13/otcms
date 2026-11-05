package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.PrescriptionLine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** UC37: authorized Patient projection of the Clinical-owned issued aggregate. */
public interface PatientPrescriptionQueryService {
    PrescriptionPage list(AuthenticatedAccount actor, UUID patientId, int page, int size);
    Detail detail(AuthenticatedAccount actor, UUID prescriptionId);

    record Patient(UUID id, String fullName, LocalDate dateOfBirth, String relationship) { }
    record Summary(UUID id, Instant issuedAt, int itemCount, String doctorName, Patient patient) { }
    record PrescriptionPage(List<Summary> items, int page, int size, long totalItems, int totalPages) {
        public PrescriptionPage { items = List.copyOf(items); }
    }
    record Detail(UUID id, UUID visitId, Instant issuedAt, String doctorName, String clinicName,
            Patient patient, String diagnosis, List<PrescriptionLine> items) {
        public Detail { items = List.copyOf(items); }
    }
}
