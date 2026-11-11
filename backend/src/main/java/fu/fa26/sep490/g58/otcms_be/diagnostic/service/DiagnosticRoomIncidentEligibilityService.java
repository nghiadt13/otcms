package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.UUID;

/** Visit/Room integration read contract for an incident tied to an unstarted imaging request. */
public interface DiagnosticRoomIncidentEligibilityService {
    IncidentRequestContext requireReportableRequest(UUID requestId, UUID roomId);
    IncidentRequestContext requireReportableOrder(UUID orderId);
    record IncidentRequestContext(UUID requestId, UUID visitId, UUID roomId,
            UUID treatingDoctorAccountId, String imagingType, String status) { }
}
