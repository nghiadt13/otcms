package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.List;
import java.util.UUID;

/**
 * Per-service-type part of room admission (phase 08). {@link ServiceQueueService} owns the queue rules (least
 * waiting room, one open queue per patient, next room); a handler only knows which rooms may perform the service
 * and how to record the room on the order. A new service type (for example LAB) adds its own bean and never
 * edits the imaging one.
 */
public interface ServiceAdmissionHandler {
    /** True for the {@code service_type} value this handler admits (for example {@code IMAGING}). */
    boolean supports(String serviceType);

    /** Rooms that can perform the order now (active, staffed, supporting the service). Empty when none. */
    List<UUID> eligibleRoomIds(UUID serviceOrderId);

    /** Records {@code roomId} on the paid order so that the service can be carried out there. Joins the caller's transaction. */
    void assignRoom(UUID serviceOrderId, UUID roomId);
}
