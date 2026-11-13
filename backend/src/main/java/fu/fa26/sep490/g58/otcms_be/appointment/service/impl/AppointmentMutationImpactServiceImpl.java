package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentOccupancyRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import java.sql.Timestamp;
import java.time.Clock;
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
    private final Clock clock;

    public AppointmentMutationImpactServiceImpl(AppointmentOccupancyRepository occupancy, Clock clock) {
        this.occupancy = occupancy;
        this.clock = clock;
    }

    /*
     * Invariant: a live payment hold (AWAITING_PAYMENT, deadline in the future) counts as a protected booking, so
     * shifts and service assignments cannot be changed under a patient who is paying. An Admin may be
     * blocked for at most the hold window by a booking no list shows.
     */

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedBookingInterval> findProtectedBookingIntervals(UUID doctorId) {
        return occupancy.findProtectedBookingIntervals(doctorId, clock.instant()).stream()
                .map(row -> new OccupiedBookingInterval(toInstant(row[0]), toInstant(row[1])))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBookingsForAssignment(UUID doctorId, UUID serviceId) {
        return occupancy.countBookingsForAssignment(doctorId, serviceId, clock.instant());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
