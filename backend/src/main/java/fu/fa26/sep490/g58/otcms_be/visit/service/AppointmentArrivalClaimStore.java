package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Visit-owned persistence contract for scheduled appointment arrival evidence. */
public interface AppointmentArrivalClaimStore {
    ArrivalClaim record(UUID appointmentId, UUID patientProfileId, UUID actorAccountId,
            String source, String reason, Instant arrivedAt);
    Optional<ArrivalClaim> find(UUID appointmentId);
    List<ArrivalClaim> list(Instant fromInclusive, Instant toExclusive, int page, int size);

    record ArrivalClaim(UUID appointmentId, UUID patientProfileId, UUID actorAccountId,
            String source, String reason, Instant arrivedAt, Instant recordedAt) { }
}
