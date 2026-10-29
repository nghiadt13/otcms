package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.UUID;

/** Public Visit-owned query contract for recorded appointment-arrival evidence. */
public interface RecordedArrivalQueryService {
    /**
     * Returns matching arrival evidence within the inclusive time window.
     *
     * @throws fu.fa26.sep490.g58.otcms_be.visit.exception.RecordedArrivalNotFoundException
     *         when no matching arrival was recorded
     */
    RecordedArrival requireRecordedArrival(UUID appointmentId, UUID patientProfileId,
            Instant windowStartInclusive, Instant windowEndInclusive);

    record RecordedArrival(UUID appointmentId, UUID patientProfileId, Instant arrivedAt,
            UUID actorAccountId) { }
}
