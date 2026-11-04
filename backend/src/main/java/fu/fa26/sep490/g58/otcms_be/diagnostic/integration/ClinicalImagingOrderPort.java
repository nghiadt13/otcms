package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import java.util.UUID;

/**
 * Clinical-owned imaging order consumed by the diagnostic module. The clinical order is the
 * source of truth for what was ordered (service, price snapshot, visit completion evidence);
 * the diagnostic request only tracks where and how the study is performed.
 */
public interface ClinicalImagingOrderPort {
    /** Returns the imaging order, or null when it does not exist, is not imaging, or is cancelled. */
    ClinicalImagingOrder requireOpenImagingOrder(UUID orderId);

    /** Marks the order as having a result; must join the caller's transaction. */
    void markResultAvailable(UUID orderId);

    /** Cancels the order when it is still open; does nothing when it is already cancelled or done. */
    void cancelOrderIfOpen(UUID orderId, String reason);
}
