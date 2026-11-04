package fu.fa26.sep490.g58.otcms_be.patient.service;

import java.util.Set;
import java.util.UUID;

public interface PatientProfileAccessService {
    Set<UUID> accessibleProfileIds(UUID accountId);
    boolean canAccess(UUID accountId, UUID profileId);
}
