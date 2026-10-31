package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visit-owned read use case for guarded Staff mutations. */
@Service
public class VisitMutationImpactServiceImpl implements VisitMutationImpactService {
    private final VisitCareAssignmentRepository assignments;

    public VisitMutationImpactServiceImpl(VisitCareAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveCareOverlapping(UUID doctorId, Instant fromInclusive, Instant toExclusive) {
        return assignments.countActiveCareOverlapping(doctorId, fromInclusive, toExclusive);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyActiveCare(UUID doctorId) {
        return assignments.countActiveCare(doctorId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareInterval> findActiveCareIntervals(UUID doctorId) {
        return assignments.findActiveCareIntervals(doctorId).stream()
                .map(row -> new CareInterval(toInstant(row[0]), toInstant(row[1])))
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
