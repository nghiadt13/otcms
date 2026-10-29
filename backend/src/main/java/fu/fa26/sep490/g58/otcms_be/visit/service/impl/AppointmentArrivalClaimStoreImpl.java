package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.AppointmentArrivalJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.AppointmentArrivalRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.AppointmentArrivalClaimStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentArrivalClaimStoreImpl implements AppointmentArrivalClaimStore {
    private final AppointmentArrivalRepository arrivals;

    public AppointmentArrivalClaimStoreImpl(AppointmentArrivalRepository arrivals) {
        this.arrivals = arrivals;
    }

    @Override
    @Transactional
    public ArrivalClaim record(UUID appointmentId, UUID patientProfileId, UUID actorAccountId,
            String source, String reason, Instant arrivedAt) {
        arrivals.insertIfAbsent(appointmentId, patientProfileId, actorAccountId, source, reason,
                Timestamp.from(arrivedAt));
        AppointmentArrivalJpaEntity row = arrivals.findById(appointmentId)
                .orElseThrow(() -> new VisitOperationException("ARRIVAL_RECORD_UNAVAILABLE"));
        if (!patientProfileId.equals(row.patientProfileId())) {
            throw new VisitOperationException("ARRIVAL_RECORD_CONFLICT");
        }
        return toClaim(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArrivalClaim> find(UUID appointmentId) {
        return arrivals.findById(appointmentId).map(AppointmentArrivalClaimStoreImpl::toClaim);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArrivalClaim> list(Instant fromInclusive, Instant toExclusive, int page, int size) {
        return arrivals.findArrivals(Timestamp.from(fromInclusive), Timestamp.from(toExclusive),
                        PageRequest.of(page, size)).stream()
                .map(AppointmentArrivalClaimStoreImpl::toClaim)
                .toList();
    }

    private static ArrivalClaim toClaim(Object[] row) {
        return new ArrivalClaim((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3],
                (String) row[4], instant(row[5]), instant(row[6]));
    }

    private static ArrivalClaim toClaim(AppointmentArrivalJpaEntity row) {
        return new ArrivalClaim(row.appointmentId(), row.patientProfileId(), row.actorAccountId(),
                row.source(), row.reason(), row.arrivedAt(), row.recordedAt());
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        throw new IllegalStateException("Unexpected arrival timestamp type");
    }
}
