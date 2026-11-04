package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.UUID;

/** Clinical-owned read boundary backed by the Visit public care-context service. */
public interface VisitClinicalContextPort {
    ClinicalVisitContext getVisitContext(UUID visitId);

    record ClinicalVisitContext(UUID visitId, UUID patientId, UUID responsibleDoctorId,
            boolean clinicalWorkAllowed) { }
}
