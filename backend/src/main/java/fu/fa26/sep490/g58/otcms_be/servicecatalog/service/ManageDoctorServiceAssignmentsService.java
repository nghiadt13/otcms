package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.DoctorAssignmentResponse;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;

public interface ManageDoctorServiceAssignmentsService {
    DoctorAssignmentResponse assign(UUID serviceId, UUID doctorId, AuthenticatedAccount actor, String correlationId);
    void remove(UUID serviceId, UUID assignmentId, AuthenticatedAccount actor, String correlationId);
    AdminMutationImpact assessRemovalImpact(UUID serviceId, UUID assignmentId, AuthenticatedAccount actor);
}
