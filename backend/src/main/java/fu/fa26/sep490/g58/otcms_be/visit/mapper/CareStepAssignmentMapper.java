package fu.fa26.sep490.g58.otcms_be.visit.mapper;

import fu.fa26.sep490.g58.otcms_be.visit.model.CareStepAssignment;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.CareStepAssignmentResponse;

public final class CareStepAssignmentMapper {
    private CareStepAssignmentMapper() { }

    public static CareStepAssignmentResponse toResponse(CareStepAssignment assignment) {
        return new CareStepAssignmentResponse(assignment.id(), assignment.visitId(), assignment.doctorProfileId(),
                assignment.stepCode(), assignment.status(), assignment.startsAt(), assignment.expectedEnd(),
                assignment.actualEndedAt(), assignment.roomId(), assignment.roomCode());
    }
}
