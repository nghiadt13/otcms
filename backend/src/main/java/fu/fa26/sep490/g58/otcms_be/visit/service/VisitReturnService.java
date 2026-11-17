package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos;
import java.util.Optional;
import java.util.UUID;

/** UC-29 AF2 and AF3: a patient returns to a visit that is Awaiting Conclusion. */
public interface VisitReturnService {
    /** The visit the patient may return to without a new fee, if any (Receptionist only, else MSG-40). */
    Optional<VisitLifecycleDtos.ReturnCandidate> findCandidate(UUID patientProfileId, AuthenticatedAccount actor);

    /**
     * AF2: the receptionist checks the patient in to the same visit (no new fee, first check-in time kept); the exam
     * entry goes back to WAITING in the room of the attending doctor with a new number and today's order.
     * AF3: when the 7-day period has ended the visit becomes Incomplete and the call is refused with MSG-42; the
     * receptionist then starts a new visit with a new fee. Unknown visit is MSG-41; Receptionist only (MSG-40).
     */
    VisitLifecycleDtos.VisitQueueTicket returnToVisit(UUID visitId, AuthenticatedAccount actor, String correlationId);
}
