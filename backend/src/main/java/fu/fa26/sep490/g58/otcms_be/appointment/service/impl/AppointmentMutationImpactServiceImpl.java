package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentOccupancyRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appointment-owned read use case for Admin mutation safeguards. */
@Service
public class AppointmentMutationImpactServiceImpl implements AppointmentMutationImpactService {
    private final AppointmentOccupancyRepository occupancy;

    public AppointmentMutationImpactServiceImpl(AppointmentOccupancyRepository occupancy) {
        this.occupancy = occupancy;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedBookingInterval> findProtectedBookingIntervals(UUID doctorId) {
        return occupancy.findProtectedBookingIntervals(doctorId).stream()
                .map(row -> new OccupiedBookingInterval(toInstant(row[0]), toInstant(row[1])))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBookingsOverlappingTimeOff(UUID doctorId, Instant fromInclusive, Instant toExclusive) {
        return occupancy.countBookingsOverlappingTimeOff(doctorId, fromInclusive, toExclusive);
    }

    @Override
    @Transactional(readOnly = true)
    public long countBookingsForAssignment(UUID doctorId, UUID serviceId) {
        return occupancy.countBookingsForAssignment(doctorId, serviceId);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
