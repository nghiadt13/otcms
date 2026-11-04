package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import java.time.Instant;
import java.util.UUID;

/** Billing owns the service price snapshot and one-obligation-per-request constraint. */
public interface ImagingBillingPort {
    UUID registerFirstUseObligation(UUID eventId, UUID requestId, UUID serviceId, Instant occurredAt);
}
