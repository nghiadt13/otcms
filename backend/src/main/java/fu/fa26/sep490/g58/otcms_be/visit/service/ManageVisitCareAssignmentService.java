package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.CareStepAssignment;
import java.time.Instant;
import java.util.UUID;

public interface ManageVisitCareAssignmentService {
    CareStepAssignment startWalkIn(StartWalkInCareStepCommand command);
    CareStepAssignment extend(ExtendVisitCareAssignmentCommand command);
    CareStepAssignment completeWalkIn(CompleteWalkInCareStepCommand command);
    void recordCapacityConflict(RecordVisitCapacityConflictCommand command);

    /**
     * Ends the responsible doctor's IN_PROGRESS care of a visit, whatever its source, and audits it as COMPLETED.
     * The caller holds the visit row lock and the coordination guard, and owns the transaction.
     */
    void completeDoctorCare(UUID visitId, UUID doctorProfileId, UUID actorAccountId, String correlationId,
            Instant actualEnd);
}
