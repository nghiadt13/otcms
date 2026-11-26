package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Released laboratory values for Visit IDs already authorized by the clinical history owner. */
public interface ClinicalLaboratoryHistoryQueryService {
    List<LaboratoryResultSummary> findReleased(Collection<UUID> visitIds);

    record LaboratoryResultSummary(UUID visitId, String serviceName, List<LaboratoryResultValue> values) {
        public LaboratoryResultSummary { values = List.copyOf(values); }
        @Override public String toString() { return "LaboratoryResultSummary[redacted]"; }
    }
}
