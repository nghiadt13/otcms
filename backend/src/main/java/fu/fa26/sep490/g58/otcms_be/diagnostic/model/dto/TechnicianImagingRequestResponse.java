package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.util.UUID;

public record TechnicianImagingRequestResponse(UUID requestId, UUID visitId, UUID patientId,
        UUID requestingDoctorAccountId, UUID serviceId, UUID roomId, String imagingType,
        String bodyArea, String clinicalReason, String instructions, DiagnosticRequestStatus status) {
    @Override public String toString() { return "TechnicianImagingRequestResponse[redacted]"; }
}
