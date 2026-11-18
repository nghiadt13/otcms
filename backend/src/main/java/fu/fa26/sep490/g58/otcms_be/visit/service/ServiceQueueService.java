package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.UUID;

/**
 * Room queue of paid service orders (stage SERVICE, D4/D7/NF-07). Every method joins the caller's transaction
 * (REQUIRED) except {@link #admitPaidOrder}, which opens its own. Imaging calls these from the diagnostic flow; the
 * laboratory flow (Long) calls the same methods with its own {@link ServiceAdmissionHandler}.
 */
public interface ServiceQueueService {
    /**
     * A service order was paid (D7). Picks the room with the fewest waiting patients among those the handler offers
     * (BR-27) when the order has none, records it, then puts the patient into the queue of the first order's room
     * unless the patient already waits in another service room. Idempotent. Orders whose service type has no
     * handler, cancelled orders and orders of a visit that is not OPEN are ignored.
     */
    void admitPaidOrder(UUID serviceOrderId);

    /**
     * The technician starts the service. Throws MSG-42 when the order's queue entry is WAITING or ABSENT (the
     * patient was not called). No entry (an order dispatched by reception before phase 08) is accepted.
     */
    void markInProgress(UUID serviceOrderId);

    /** The service was carried out (study sent, results approved): the stay ends and the patient moves to the next room. */
    void complete(UUID serviceOrderId);

    /** The service will not be carried out in this room (cannot perform, cancelled): the entry leaves, next room follows. */
    void leave(UUID serviceOrderId);

    /**
     * Reception dispatched a paid order to a room by hand: joins the caller's transaction and gives the order its place
     * in that room's queue under the same one-queue-at-a-time rule. No-op for unpaid or room-less orders.
     */
    void enqueueDispatchedOrder(UUID serviceOrderId);

    /**
     * The visit ended (the doctor closed it): open SERVICE entries are cancelled and the open RESULT_REVIEW entry is
     * completed. No next order is promoted. Idempotent.
     */
    void closeEntriesOfVisit(UUID visitId);
}
