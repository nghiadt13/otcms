package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import java.util.UUID;

public interface RequestCareRelationshipService {
    CareRelationship request(UUID actorAccountId, UUID dependentPatientProfileId);
}
