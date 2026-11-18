package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import java.util.UUID;

public interface DiagnosticAcquisitionService {
    UUID start(DiagnosticActor actor, UUID requestId);

    /**
     * UC-46 AF3: the technician cannot perform the order (contraindication, patient refuses, equipment down, patient
     * must come back). The patient leaves the room queue and the next paid order of the visit takes its turn; the
     * order itself stays unchanged so that the requesting doctor decides (cancel or replace). Only a PENDING order of
     * the technician's own room qualifies; a blank reason is MSG-39. The reason goes to the audit log.
     */
    void reportCannotPerform(DiagnosticActor actor, UUID requestId, String reason);
}
