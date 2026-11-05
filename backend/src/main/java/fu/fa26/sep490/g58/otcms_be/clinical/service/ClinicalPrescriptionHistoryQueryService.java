package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.PrescriptionLine;
import java.time.Instant;
import java.util.*;

/** Trusted owner query for future UC33 composition. Caller authorizes access before selecting IDs. No HTTP endpoint. */
public interface ClinicalPrescriptionHistoryQueryService {
    List<IssuedPrescriptionSummary> findIssuedByPatient(UUID patientProfileId);
    List<IssuedPrescriptionSummary> findIssuedByVisit(UUID visitId);

    record IssuedPrescriptionSummary(UUID id, UUID visitId, UUID patientProfileId, UUID doctorProfileId,
            Instant issuedAt, long version, List<PrescriptionLine> items) {
        public IssuedPrescriptionSummary { items = List.copyOf(items); }
        @Override public String toString() { return "IssuedPrescriptionSummary[redacted]"; }
    }
}
