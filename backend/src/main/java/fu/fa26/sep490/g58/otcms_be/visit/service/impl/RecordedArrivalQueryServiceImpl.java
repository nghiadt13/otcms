package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.exception.RecordedArrivalNotFoundException;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordedArrivalQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordedArrivalQueryService.RecordedArrival;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitEvidenceException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitInitialReceptionJpaRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visit-owned public query for recorded appointment arrival evidence. */
@Service
public class RecordedArrivalQueryServiceImpl implements RecordedArrivalQueryService {
    private final VisitInitialReceptionJpaRepository receptions;

    public RecordedArrivalQueryServiceImpl(VisitInitialReceptionJpaRepository receptions) {
        this.receptions = receptions;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = RecordedArrivalNotFoundException.class)
    public RecordedArrival requireRecordedArrival(UUID appointmentId, UUID patientProfileId,
            Instant windowStartInclusive, Instant windowEndInclusive) {
        if (appointmentId == null || patientProfileId == null || windowStartInclusive == null
                || windowEndInclusive == null || windowStartInclusive.isAfter(windowEndInclusive)) {
            throw new VisitEvidenceException("RECORDED_ARRIVAL_WINDOW_INVALID");
        }
        List<RecordedArrival> arrivals = receptions.findRecordedArrivalEvidence(appointmentId, patientProfileId,
                        Timestamp.from(windowStartInclusive), Timestamp.from(windowEndInclusive)).stream()
                .map(row -> new RecordedArrival((UUID) row[0], (UUID) row[1], toInstant(row[2]), (UUID) row[3]))
                .toList();
        if (arrivals.isEmpty()) throw new RecordedArrivalNotFoundException();
        return arrivals.getFirst();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
