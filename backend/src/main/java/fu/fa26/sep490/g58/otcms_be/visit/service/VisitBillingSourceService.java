package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.UUID;

/** Public visit projection needed by the billing feature; the visit module owns persistence. */
public interface VisitBillingSourceService {
    BillingSource requireClosedVisit(UUID visitId);

    record BillingSource(UUID visitId, UUID patientProfileId) { }
}
