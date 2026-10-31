package fu.fa26.sep490.g58.otcms_be.patient.service;

import java.util.UUID;
import java.util.List;

/** Authorization specifically for billing records and confirmed care relationships. */
public interface AuthorizePatientBillingProfileService {
    void authorize(UUID accountId, UUID patientProfileId);
    List<UUID> accessibleProfileIds(UUID accountId);
}
