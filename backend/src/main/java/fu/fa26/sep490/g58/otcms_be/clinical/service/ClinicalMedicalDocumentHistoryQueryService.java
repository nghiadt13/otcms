package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.*;
import java.time.Instant;

/** Trusted metadata query; caller authorizes patient and historical Visit scope. Content remains UC32. */
public interface ClinicalMedicalDocumentHistoryQueryService {
    List<DocumentSummary> findReleased(UUID patientProfileId, Collection<UUID> visitIds);

    record DocumentSummary(UUID id, UUID visitId, UUID patientProfileId, String documentType,
            String title, String mediaType, Instant releasedAt) {
        public boolean released() { return releasedAt != null; }
        @Override public String toString() { return "DocumentSummary[redacted]"; }
    }
}
