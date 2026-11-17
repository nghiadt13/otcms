package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos;
import java.util.UUID;

/** UC-31 AF1, BR-27: change the room of a service order that already has one (any service type). */
public interface ServiceOrderRoomTransferService {
    /**
     * Receptionist only (MSG-40). Unknown order or room MSG-41; order not movable (cancelled, has results, already in
     * progress), room unsuitable (inactive, does not support the service), same room, visit not OPEN, or the patient
     * is being served MSG-42; blank reason MSG-39. Moves the patient's waiting SERVICE queue entry too.
     */
    VisitLifecycleDtos.ServiceOrderRoomTransfer transfer(UUID orderId, UUID newRoomId, String reason,
            AuthenticatedAccount actor, String correlationId);

    /** The room of the order was changed elsewhere (doctor transfer of an imaging request): follow it in the queue. */
    void followRoomChange(UUID orderId, UUID newRoomId);
}
