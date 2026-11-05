package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.*;

/** Trusted Visit-owned historical scope; caller authorizes current-care access first. */
public interface VisitPatientHistoryQueryService {
    HistoryPage findPastVisits(UUID patientProfileId, UUID currentVisitId, int page, int size);
    record HistoricalVisit(UUID visitId, UUID patientProfileId, Instant arrivedAt, String status) { }
    record HistoryPage(List<HistoricalVisit> items, long total) {
        public HistoryPage { items = List.copyOf(items); }
    }
}
