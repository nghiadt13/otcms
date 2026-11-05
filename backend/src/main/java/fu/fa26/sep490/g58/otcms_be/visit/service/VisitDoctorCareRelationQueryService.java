package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.UUID;

/** Current-care authorization fact only; does not grant historical access. */
public interface VisitDoctorCareRelationQueryService {
    boolean hasCurrentCare(UUID doctorProfileId, UUID patientProfileId);
}
