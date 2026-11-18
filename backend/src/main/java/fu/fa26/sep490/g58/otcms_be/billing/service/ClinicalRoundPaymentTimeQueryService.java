package fu.fa26.sep490.g58.otcms_be.billing.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ClinicalRoundPaymentTimeQueryService {
    Map<UUID, Instant> confirmedPaymentTimes(Collection<UUID> roundIds);
}
