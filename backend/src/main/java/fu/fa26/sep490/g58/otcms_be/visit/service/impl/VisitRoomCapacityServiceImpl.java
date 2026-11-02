package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.config.VisitCareProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitRoomCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomReservationRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VisitRoomCapacityServiceImpl implements VisitRoomCapacityService {
    private final VisitCareProperties properties;
    private final VisitRoomReservationRepository reservations;

    public VisitRoomCapacityServiceImpl(VisitCareProperties properties,
            VisitRoomReservationRepository reservations) {
        this.properties = properties;
        this.reservations = reservations;
    }

    @Override
    public RoomReservationPlan prepare(String stepCode, Instant startsAt, Instant expectedEndsAt,
            UUID excludedAssignmentId) {
        String capability = properties.roomCapability(stepCode)
                .orElseThrow(() -> new VisitOperationException("VISIT_CARE_STEP_NOT_CONFIGURED"));
        if (!startsAt.isBefore(expectedEndsAt)) throw new VisitOperationException("VALIDATION_FAILED");
        if ("NONE".equals(capability)) {
            return new RoomReservationPlan(null, null, stepCode, capability, startsAt, expectedEndsAt,
                    expectedEndsAt, 0);
        }
        int buffer = properties.cleanupBufferMinutes(capability)
                .orElseThrow(() -> new VisitOperationException("VISIT_CARE_STEP_NOT_CONFIGURED"));
        Instant reservedUntil = safePlusMinutes(expectedEndsAt, buffer);
        RoomReservationPlan plan = reservations.findAvailableRoom(stepCode, capability, startsAt, expectedEndsAt,
                reservedUntil, buffer, excludedAssignmentId);
        if (plan == null) throw new VisitRoomCapacityConflictException();
        return plan;
    }

    @Override
    public void reserve(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt) {
        if (plan.roomId() != null) {
            reservations.insert(plan, visitId, assignmentId, actorAccountId, correlationId, occurredAt);
        }
    }

    @Override
    public void extend(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt) {
        reservations.extend(plan, visitId, assignmentId, actorAccountId, correlationId, occurredAt);
    }

    @Override
    public void complete(UUID visitId, UUID assignmentId, UUID actorAccountId, String correlationId,
            Instant actualEndedAt) {
        reservations.complete(visitId, assignmentId, actorAccountId, correlationId, actualEndedAt);
    }

    @Override
    public void recordConflict(UUID visitId, UUID assignmentId, UUID actorAccountId, String stepCode,
            String correlationId, Instant occurredAt) {
        reservations.appendConflict(visitId, assignmentId, actorAccountId, stepCode, correlationId, occurredAt);
    }

    private static Instant safePlusMinutes(Instant instant, int minutes) {
        try {
            return instant.plusSeconds(Math.multiplyExact((long) minutes, 60));
        } catch (ArithmeticException exception) {
            throw new VisitOperationException("VALIDATION_FAILED", exception);
        }
    }
}
