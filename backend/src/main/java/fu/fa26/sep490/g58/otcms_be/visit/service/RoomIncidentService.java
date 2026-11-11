package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.RoomIncidentJpaEntity;
import java.util.List;
import java.util.UUID;

public interface RoomIncidentService {
    IncidentView report(UUID requestId, UUID roomId, UUID actorId, String idempotencyKey,
            String type, String description, String correlationId);
    List<IncidentView> listPending(int page, int size);
    IncidentView decide(UUID incidentId, UUID actorId, String decision, String reason, String correlationId);
    IncidentView restore(UUID incidentId, UUID actorId, String reason, String correlationId);
    boolean verifyNoAlternativeForRefund(UUID incidentId, UUID requestId);

    record IncidentView(UUID incidentId, UUID requestId, UUID visitId, UUID roomId, String type,
            String description, String status, UUID reportedBy, UUID decidedBy, String decisionReason,
            java.time.Instant reportedAt, java.time.Instant decidedAt, long version) {
        public static IncidentView from(RoomIncidentJpaEntity entity) {
            return new IncidentView(entity.getId(), entity.getDiagnosticRequestId(), entity.getVisitId(),
                    entity.getRoomId(), entity.getIncidentType(), entity.getDescription(), entity.getStatus(),
                    entity.getReportedByAccountId(), entity.getDecidedByAccountId(), entity.getDecisionReason(),
                    entity.getReportedAt(), entity.getDecidedAt(), entity.getVersion());
        }
    }
}
