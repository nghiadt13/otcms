package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Visit-owned read projection for a Patient's authorized profile history. */
public interface PatientPortalVisitHistoryQueryService {
    VisitPage findVisits(List<UUID> patientProfileIds, int page, int size);
    Optional<PatientVisit> findVisit(UUID visitId, List<UUID> patientProfileIds);
    Optional<UUID> findPreviousVisit(PatientVisit visit);
    Optional<UUID> findNextVisit(PatientVisit visit);

    record PatientVisit(UUID visitId, UUID patientProfileId, Instant arrivedAt, String status,
            String source, UUID originatingAppointmentId) { }
    record VisitPage(List<PatientVisit> items, int page, int size, long totalItems, int totalPages) {
        public VisitPage { items = List.copyOf(items); }
    }
}
