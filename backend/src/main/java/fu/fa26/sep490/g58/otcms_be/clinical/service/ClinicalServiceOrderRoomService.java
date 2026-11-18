package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.Optional;
import java.util.UUID;

/** Room of an already assigned service order, for any service type (UC-31 AF1). Joins the caller's transaction. */
public interface ClinicalServiceOrderRoomService {
    /**
     * @param roomId current room, null while no room is assigned
     * @param movable ORDERED, a room is assigned, and (for imaging) the request is still PENDING
     */
    record OrderRoom(UUID orderId, UUID visitId, UUID serviceId, String serviceType, String imagingType,
            UUID roomId, boolean movable) { }

    /** Locks the order row (SELECT ... FOR UPDATE); empty when the order does not exist. */
    Optional<OrderRoom> lockForTransfer(UUID orderId);

    /** Moves a movable order to {@code roomId}; false when the order is no longer in a movable state. */
    boolean changeRoom(UUID orderId, UUID roomId);
}
