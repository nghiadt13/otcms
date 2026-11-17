package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos;
import java.util.UUID;

/** UC-31 AF2, BR-34: the receptionist moves a visit to the doctor on duty when the attending doctor is absent. */
public interface AttendingDoctorTransferService {
    /**
     * Changes the attending doctor of an OPEN visit, moves its exam entry to the new doctor (new number and room,
     * check-in order kept) and notifies the new doctor with MSG-83. Receptionist only (MSG-40); blank reason MSG-39;
     * unknown visit or no doctor on duty MSG-41; visit or entry in a state that cannot move MSG-42.
     */
    VisitLifecycleDtos.VisitQueueTicket transfer(UUID visitId, UUID newDoctorProfileId, String reason,
            AuthenticatedAccount actor, String correlationId);
}
