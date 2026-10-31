package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public Visit queries used by guarded Staff and Service Catalog mutations. */
public interface VisitMutationImpactService {
    long countActiveCareOverlapping(UUID doctorId, Instant fromInclusive, Instant toExclusive);
    boolean hasAnyActiveCare(UUID doctorId);
    List<CareInterval> findActiveCareIntervals(UUID doctorId);

    class CareInterval {
        private final Instant startsAt;
        private final Instant endsAt;

        public CareInterval(Instant startsAt, Instant endsAt) {
            this.startsAt = startsAt;
            this.endsAt = endsAt;
        }

        public Instant startsAt() { return startsAt; }
        public Instant endsAt() { return endsAt; }
    }
}
