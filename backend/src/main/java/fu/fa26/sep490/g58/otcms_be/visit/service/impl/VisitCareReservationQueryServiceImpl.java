package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.service.VisitCareReservationQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitCareReservationQueryService.CareReservation;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visit-owned bounded reservation query consumed by Appointment slot generation. */
@Service
public class VisitCareReservationQueryServiceImpl implements VisitCareReservationQueryService {
    private final VisitCareAssignmentRepository assignments;

    public VisitCareReservationQueryServiceImpl(VisitCareAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareReservation> findActiveCareReservations(Collection<UUID> doctorProfileIds,
            Instant fromInclusive, Instant toExclusive) {
        if (doctorProfileIds == null || doctorProfileIds.isEmpty() || doctorProfileIds.stream().anyMatch(id -> id == null)
                || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Doctor IDs and a positive bounded interval are required");
        }
        List<UUID> doctorIds = doctorProfileIds.stream().distinct().sorted().toList();
        return assignments.findActiveCareReservationRows(doctorIds, Timestamp.from(fromInclusive), Timestamp.from(toExclusive))
                .stream().map(row -> new CareReservation((UUID) row[0], (UUID) row[1], (UUID) row[2],
                        (UUID) row[3], toInstant(row[4]), toInstant(row[5])))
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
