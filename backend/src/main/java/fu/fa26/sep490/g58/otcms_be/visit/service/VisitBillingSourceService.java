package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.UUID;

/** Public visit projection needed by the billing feature; the visit module owns persistence. */
public interface VisitBillingSourceService {
    /**
     * Visit that billing may invoice now: a CLOSED visit (not one cancelled or closed while its walk-in fee was
     * still pending), or an OPEN walk-in waiting for its fee. Otherwise VISIT_NOT_BILLABLE.
     */
    BillingSource requireBillableVisit(UUID visitId);

    /**
     * requiredServiceId: the only service a pending walk-in fee may be invoiced for (quantity 1); null when the
     * visit has no such restriction.
     */
    record BillingSource(UUID visitId, UUID patientProfileId, UUID requiredServiceId) { }
}
