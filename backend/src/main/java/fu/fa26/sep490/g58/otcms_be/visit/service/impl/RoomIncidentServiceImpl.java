package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRoomIncidentEligibilityService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.RoomIncidentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.RoomIncidentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RoomIncidentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomIncidentServiceImpl implements RoomIncidentService {
    private final RoomIncidentRepository incidents;
    private final ManageVisitRoomService rooms;
    private final StaffShiftAdminService shifts;
    private final DiagnosticRoomIncidentEligibilityService diagnostic;
    private final NotificationPublisher notifications;
    private final RoomReservationPort roomAccess;
    private final Clock clock;

    public RoomIncidentServiceImpl(RoomIncidentRepository incidents, ManageVisitRoomService rooms,
            StaffShiftAdminService shifts, DiagnosticRoomIncidentEligibilityService diagnostic,
            NotificationPublisher notifications, RoomReservationPort roomAccess, Clock clock) {
        this.incidents = incidents;
        this.rooms = rooms;
        this.shifts = shifts;
        this.diagnostic = diagnostic;
        this.notifications = notifications;
        this.roomAccess = roomAccess;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IncidentView report(UUID requestId, UUID roomId, UUID actorId, String idempotencyKey,
            String type, String description, String correlationId) {
        requireText(idempotencyKey, 128);
        requireText(correlationId, 128);
        String normalizedType = requireText(type, 40);
        String normalizedDescription = requireText(description, 2000);
        if (actorId == null || requestId == null || roomId == null
                || !List.of("MACHINE_FAILURE", "ROOM_UNAVAILABLE", "OTHER").contains(normalizedType)) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        String fingerprint = digest(requestId + "|" + roomId + "|" + normalizedType + "|" + normalizedDescription);
        var prior = incidents.findFirstByReportedByAccountIdAndReportIdempotencyKey(actorId, idempotencyKey);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getReportFingerprint())) throw new VisitOperationException("IDEMPOTENCY_CONFLICT");
            return IncidentView.from(prior.get());
        }
        if (!shifts.isActiveImagingTechnicianAssignedToRoom(actorId, roomId, clock.instant())) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        var context = diagnostic.requireReportableRequest(requestId, roomId);
        var room = rooms.get(roomId);
        if (!room.active() || !"IMAGING".equalsIgnoreCase(room.type())) throw new VisitOperationException("ROOM_NOT_AVAILABLE");
        var incident = incidents.saveAndFlush(new RoomIncidentJpaEntity(UUID.randomUUID(), roomId, requestId,
                context.visitId(), actorId, idempotencyKey, fingerprint, normalizedType, normalizedDescription));
        notifications.publishToRole(AccountRole.ADMIN, new NotificationMessage("ROOM_INCIDENT_REPORTED",
                "Cần xác nhận sự cố phòng chụp", "Có yêu cầu sự cố cần Admin quản lý phòng xác nhận.",
                NotificationRelatedKind.VISIT, context.visitId()));
        return IncidentView.from(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentView> listPending(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new VisitOperationException("VALIDATION_FAILED");
        return incidents.findAllByStatusOrderByReportedAtAscIdAsc("REPORTED", PageRequest.of(page, size))
                .stream().map(IncidentView::from).toList();
    }

    @Override
    @Transactional
    public IncidentView decide(UUID incidentId, UUID actorId, String decision, String reason, String correlationId) {
        if (actorId == null || incidentId == null || !"CONFIRM".equals(decision) && !"REJECT".equals(decision)) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        String normalizedReason = requireText(reason, 1000);
        requireText(correlationId, 128);
        RoomIncidentJpaEntity incident = incidents.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new VisitOperationException("ROOM_INCIDENT_NOT_FOUND"));
        String target = "CONFIRM".equals(decision) ? "CONFIRMED" : "REJECTED";
        if (!"REPORTED".equals(incident.getStatus())) {
            if (target.equals(incident.getStatus()) && actorId.equals(incident.getDecidedByAccountId())
                    && normalizedReason.equals(incident.getDecisionReason())) return IncidentView.from(incident);
            throw new VisitOperationException("STATE_CONFLICT");
        }
        var context = diagnostic.requireReportableRequest(incident.getDiagnosticRequestId(), incident.getRoomId());
        if ("CONFIRM".equals(decision)) {
            rooms.deactivate(incident.getRoomId(), actorId, correlationId);
        }
        incident.decide(actorId, target, normalizedReason, clock.instant());
        incidents.saveAndFlush(incident);
        if ("CONFIRM".equals(decision)) {
            notifications.publish(context.treatingDoctorAccountId(), new NotificationMessage("ROOM_INCIDENT_CONFIRMED",
                    "Phòng chụp tạm ngưng", "Phòng được chỉ định đã tạm ngưng nhận yêu cầu mới; hãy chuyển chỉ định chưa thực hiện nếu có phòng phù hợp.",
                    NotificationRelatedKind.VISIT, context.visitId()));
        } else {
            notifications.publish(incident.getReportedByAccountId(), new NotificationMessage("ROOM_INCIDENT_REJECTED",
                    "Yêu cầu sự cố phòng bị từ chối", normalizedReason, NotificationRelatedKind.VISIT, context.visitId()));
        }
        return IncidentView.from(incident);
    }

    @Override
    @Transactional
    public IncidentView restore(UUID incidentId, UUID actorId, String reason, String correlationId) {
        if (incidentId == null || actorId == null) throw new VisitOperationException("VALIDATION_FAILED");
        String normalizedReason = requireText(reason, 1000);
        requireText(correlationId, 128);
        RoomIncidentJpaEntity incident = incidents.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new VisitOperationException("ROOM_INCIDENT_NOT_FOUND"));
        if ("RESTORED".equals(incident.getStatus()) && actorId.equals(incident.getDecidedByAccountId())
                && normalizedReason.equals(incident.getDecisionReason())) return IncidentView.from(incident);
        if (!"CONFIRMED".equals(incident.getStatus())) throw new VisitOperationException("STATE_CONFLICT");
        if (!incidents.existsByRoomIdAndStatusAndIdNot(incident.getRoomId(), "CONFIRMED", incident.getId())) {
            rooms.activate(incident.getRoomId(), actorId, correlationId);
        }
        incident.restore(actorId, normalizedReason, clock.instant());
        incidents.saveAndFlush(incident);
        return IncidentView.from(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyNoAlternativeForRefund(UUID incidentId, UUID requestId) {
        if (incidentId == null || requestId == null) return false;
        RoomIncidentJpaEntity incident = incidents.findById(incidentId).orElse(null);
        if (incident == null || !"CONFIRMED".equals(incident.getStatus())) return false;
        var context = diagnostic.requireReportableOrder(requestId);
        if (!context.requestId().equals(incident.getDiagnosticRequestId())
                || !incident.getRoomId().equals(context.roomId())) return false;
        return !roomAccess.hasEligibleAlternative(incident.getRoomId(), context.imagingType());
    }

    private static String requireText(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new VisitOperationException("VALIDATION_FAILED");
        return value.trim();
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
}
