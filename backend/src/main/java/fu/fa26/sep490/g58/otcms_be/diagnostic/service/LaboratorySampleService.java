package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.time.Instant;
import java.util.UUID;

/** Persistence boundary for sample collection. Callers must authorize the order first. */
public interface LaboratorySampleService {
    Sample collect(UUID orderId, UUID accountId, Instant now);
    Sample latest(UUID orderId);
    record Sample(UUID id, String code, int attempt, Instant collectedAt) { }
}
