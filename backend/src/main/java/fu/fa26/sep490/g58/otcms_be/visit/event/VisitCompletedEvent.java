package fu.fa26.sep490.g58.otcms_be.visit.event;

import java.util.UUID;

/**
 * The responsible doctor closed the visit (UC-36). Published inside the closing transaction and delivered after it
 * commits, so a listener never sees a visit that was rolled back. Phase 06 listens to issue the result sheet.
 *
 * @param doctorId profile id of the doctor who closed the visit
 */
public record VisitCompletedEvent(UUID visitId, UUID doctorId) { }
