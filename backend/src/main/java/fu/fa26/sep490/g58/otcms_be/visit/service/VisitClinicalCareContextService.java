package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.Optional;
import java.util.UUID;

/** Visit-owned facts about actual current care, not historical Doctor access or a lifecycle command. */
public interface VisitClinicalCareContextService {
    /** Null or missing visit IDs return empty; infrastructure failures propagate. */
    Optional<ClinicalCareContext> findClinicalCareContext(UUID visitId);
    java.util.Map<UUID, ClinicalCareContext> findClinicalCareContexts(java.util.Collection<UUID> visitIds);

    record ClinicalCareContext(UUID visitId, UUID patientProfileId, UUID activeCareAssignmentId,
            UUID responsibleDoctorProfileId, boolean visitOpen, boolean clinicalWorkAllowed) { }
}
