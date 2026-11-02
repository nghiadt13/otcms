package fu.fa26.sep490.g58.otcms_be.visit.mapper;

import fu.fa26.sep490.g58.otcms_be.visit.model.CreatedWalkInVisit;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.WalkInVisitResponse;

public final class WalkInVisitMapper {
    private WalkInVisitMapper() { }

    public static WalkInVisitResponse toResponse(CreatedWalkInVisit visit) {
        return new WalkInVisitResponse(visit.visitId(), visit.patientProfileId(), visit.priorAppointmentId(),
                visit.arrivedAt());
    }
}
