package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.UUID;

/** Clinical-owned cancellation of orders that were never carried out (visit becomes Incomplete, BR-65). */
public interface ClinicalUnperformedOrderService {
    /**
     * Cancels every order of the visit that is still ORDERED and not performed: a service without a request, or an
     * imaging request still PENDING. Orders already performed (in progress or with a result) stay as they are.
     * Joins the caller's transaction. Idempotent: a second call finds nothing to cancel.
     *
     * @return ids of the orders cancelled by this call; billing decides separately what happens to their fee items
     */
    List<UUID> cancelUnperformedOrders(UUID visitId, String reason);
}
