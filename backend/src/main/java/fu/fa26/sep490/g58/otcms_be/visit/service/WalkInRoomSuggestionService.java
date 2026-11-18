package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos;

/** BR-27 / UC-29 AF1: the examination room with the fewest waiting patients among rooms with a doctor on shift. */
public interface WalkInRoomSuggestionService {
    /** Receptionist only (MSG-40). Empty list when no doctor is on shift. */
    VisitLifecycleDtos.WalkInSuggestions suggest(AuthenticatedAccount actor);
}
