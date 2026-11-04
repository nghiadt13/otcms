package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalVisitStatus;
import java.util.UUID;

/** Clinical-owned boundary; adapter is supplied by the visit feature owner. */
public interface VisitClinicalContextPort {
    ClinicalVisitContext getVisitContext(UUID visitId);

    /**
     * Visit integration command; adapters must treat an already-awaiting visit as success so
     * callers can safely retry after Clinical's local transaction has committed.
     */
    void markAwaitingConclusion(UUID visitId, UUID responsibleDoctorId);

    record ClinicalVisitContext(UUID visitId, UUID patientId, UUID responsibleDoctorId,
            ClinicalVisitStatus status) { }
}
