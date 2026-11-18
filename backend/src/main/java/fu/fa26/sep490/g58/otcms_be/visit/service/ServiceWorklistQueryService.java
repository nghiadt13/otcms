package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ServiceWorklistDtos;
import java.util.UUID;

public interface ServiceWorklistQueryService {
    /**
     * Today's open SERVICE queue of the room in queue order, each entry with the patient's minimum header and the
     * patient's orders for this room. Receptionist or the technician assigned to the room, else MSG-40; unknown
     * room MSG-41.
     */
    ServiceWorklistDtos.Worklist worklist(UUID roomId, AuthenticatedAccount actor);
}
