package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import java.time.Instant;
import java.util.UUID;

public interface VisitRoomCapacityService {
    RoomReservationPlan prepare(String stepCode, Instant startsAt, Instant expectedEndsAt,
            UUID excludedAssignmentId);
    void reserve(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt);
    void extend(RoomReservationPlan plan, UUID visitId, UUID assignmentId, UUID actorAccountId,
            String correlationId, Instant occurredAt);
    void complete(UUID visitId, UUID assignmentId, UUID actorAccountId, String correlationId,
            Instant actualEndedAt);
    void recordConflict(UUID visitId, UUID assignmentId, UUID actorAccountId, String stepCode,
            String correlationId, Instant occurredAt);
}
