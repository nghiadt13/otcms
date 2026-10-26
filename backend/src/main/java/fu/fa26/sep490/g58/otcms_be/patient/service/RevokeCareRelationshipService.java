package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import java.util.UUID;

public interface RevokeCareRelationshipService {
    CareRelationship revoke(UUID actorAccountId, UUID relationshipId);
}
