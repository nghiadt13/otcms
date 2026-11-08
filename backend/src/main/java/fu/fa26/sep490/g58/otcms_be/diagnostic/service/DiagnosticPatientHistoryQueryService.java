package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import java.util.*;

/** Trusted UC33 owner query. Caller first authorizes current care and selects eligible historical Visits.
 * Does not require the current Doctor to have treated the historical Visit. No HTTP endpoint. */
public interface DiagnosticPatientHistoryQueryService {
    List<HistoricalImagingResult> findReleased(UUID patientProfileId, Collection<UUID> historicalVisitIds,
            UUID actorAccountId, int page, int size);
    record HistoricalImagingResult(UUID patientProfileId, DiagnosticHistoryItem result) {
        @Override public String toString() { return "HistoricalImagingResult[redacted]"; }
    }
}
