package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.CareStepAssignment;

public interface ManageVisitCareAssignmentService {
    CareStepAssignment startWalkIn(StartWalkInCareStepCommand command);
    CareStepAssignment extend(ExtendVisitCareAssignmentCommand command);
    CareStepAssignment completeWalkIn(CompleteWalkInCareStepCommand command);
    void recordCapacityConflict(RecordVisitCapacityConflictCommand command);
}
