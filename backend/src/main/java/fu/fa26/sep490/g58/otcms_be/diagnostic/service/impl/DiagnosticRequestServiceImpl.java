package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomReservation;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedImagingWork;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAttempt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRequestService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticRequestServiceImpl implements DiagnosticRequestService {
    private static final String CREATE_OPERATION = "CREATE_REQUEST";
    private static final int WORK_SCAN_SIZE = 100;
    private static final List<DiagnosticRequestStatus> UPLOAD_STATUSES =
            List.of(DiagnosticRequestStatus.PENDING, DiagnosticRequestStatus.IN_PROGRESS);

    private final ClinicalServiceOrderExecutionPort requests;
    private final AuditLogWriter auditEvents;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts;
    private final ObjectProvider<RoomReservationPort> roomPorts;
    private final ObjectProvider<ImagingServiceCatalogPort> catalogPorts;
    private final ObjectProvider<ClinicalImagingOrderPort> orderPorts;
    private final ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService> queueRooms;
    private final ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService> orderRoomTransfers;

    public DiagnosticRequestServiceImpl(ClinicalServiceOrderExecutionPort requests,
            AuditLogWriter auditEvents,
            DiagnosticAuthorizationPolicy authorization,
            ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts,
            ObjectProvider<RoomReservationPort> roomPorts,
            ObjectProvider<ImagingServiceCatalogPort> catalogPorts,
            ObjectProvider<ClinicalImagingOrderPort> orderPorts,
            ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService> queueRooms,
            ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService> orderRoomTransfers) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.roomPorts = Objects.requireNonNull(roomPorts, "roomPorts");
        this.catalogPorts = Objects.requireNonNull(catalogPorts, "catalogPorts");
        this.orderPorts = Objects.requireNonNull(orderPorts, "orderPorts");
        this.queueRooms = Objects.requireNonNull(queueRooms, "queueRooms");
        this.orderRoomTransfers = orderRoomTransfers;
    }

    @Override
    @Transactional
    public DiagnosticRequestView create(DiagnosticActor actor, UUID visitId, String idempotencyKey,
            UUID serviceId, String imagingType, String bodyArea, String clinicalReason,
            UUID roomId, UUID serviceOrderId, String correlationId) {
        // Legacy direct creation is retired. Only Reception's paid Clinical round dispatch registers work.
        throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticRequestPage listVisitRequests(DiagnosticActor actor, UUID visitId, int page, int size) {
        requireDoctor(actor);
        requirePage(page, size);
        Page<ImagingExecution> result = requests.page(visitId, null, actor.accountId(), null, null, false, PageRequest.of(page, size));
        return new DiagnosticRequestPage(result.getContent().stream().map(this::toView).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticRequestView get(DiagnosticActor actor, UUID requestId) {
        requireDoctor(actor);
        ImagingExecution entity = requests.find(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (!actor.accountId().equals(entity.orderingDoctorAccountId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return toView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticRequestPage listAssignedUploadWork(DiagnosticActor actor, UUID roomId, int page, int size) {
        requireImagingTechnician(actor);
        requirePage(page, size);
        long start = (long) page * size;
        long end = start + size;
        long visible = 0;
        List<DiagnosticRequestView> selected = new ArrayList<>(size);
        int batchNumber = 0;
        Page<ImagingExecution> batch;
        Map<String, RoomEvidence> roomCache = new LinkedHashMap<>();
        do {
            Pageable pageable = PageRequest.of(batchNumber++, WORK_SCAN_SIZE);
            batch = requests.page(null, null, null, roomId,
                    UPLOAD_STATUSES.stream().map(s -> ExecutionStatus.valueOf(s.name())).toList(), true, pageable);
            for (ImagingExecution request : batch.getContent()) {
                if (request.roomId() == null) continue;
                if (roomId != null && !roomId.equals(request.roomId())) continue;
                if (roomPorts.getIfAvailable() == null) {
                    if (!isAssignedQueueTechnician(actor, request)) continue;
                    if (visible >= start && visible < end) selected.add(toView(request));
                    visible++;
                    continue;
                }
                String cacheKey = request.roomId() + "|" + request.imagingType();
                RoomEvidence room = roomCache.computeIfAbsent(cacheKey,
                        ignored -> requireRoomEvidence(request.roomId(), request.imagingType()));
                if (room.roomId() == null || !request.roomId().equals(room.roomId())) {
                    throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
                }
                if (!actor.accountId().equals(room.assignedTechnicianId())) continue;
                if (visible >= start && visible < end) selected.add(toView(request));
                visible++;
            }
        } while (batch.hasNext());
        return new DiagnosticRequestPage(selected, page, size, visible);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticAssignedImagingWork requireAssignedImagingWork(DiagnosticActor actor,
            UUID requestId, UUID attemptId) {
        requireImagingTechnician(actor);
        if (requestId == null || attemptId == null) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        ImagingExecution request = requests.find(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD));
        if (!UPLOAD_STATUSES.contains(DiagnosticRequestStatus.valueOf(request.status().name())) || !attemptId.equals(request.attemptId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        if (roomPorts.getIfAvailable() == null) {
            if (!isAssignedQueueTechnician(actor, request)) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
            }
            return new DiagnosticAssignedImagingWork(requestId, attemptId, request.visitId(),
                    request.patientId(), Long.toString(request.executionVersion()), true);
        }
        RoomEvidence room = requireRoomEvidence(request.roomId(), request.imagingType());
        if (room.roomId() == null || !request.roomId().equals(room.roomId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        boolean assigned = room.operational() && room.compatible()
                && actor.accountId().equals(room.assignedTechnicianId());
        if (!assigned) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return new DiagnosticAssignedImagingWork(requestId, attemptId, request.visitId(),
                request.patientId(), Long.toString(room.roomVersion()), true);
    }

    @Override
    @Transactional
    public DiagnosticRequestView cancel(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, CancellationCause cause, String reason, String correlationId) {
        requireDoctor(actor);
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        requireText(correlationId, "correlationId");
        String operation = DiagnosticAction.MANUAL_CANCEL.name();
        String fingerprint = fingerprint(cause.name() + "\n" + reason.trim() + "\n" + expectedVersion);
        ImagingExecution request = requests.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        VisitEvidence visit = requireDoctorVisit(actor, request);
        if (idempotencyKey.equals(request.cancelIdempotencyKey())) return replay(request, request.cancelFingerprint(), fingerprint);
        DiagnosticRequest domain = toDomain(request);
        DiagnosticEvidenceContext evidence = evidence(request, visit);
        authorization.requireAllowed(domain, DiagnosticAction.MANUAL_CANCEL, actor, evidence, expectedVersion);

        if (requests.cancel(requestId, expectedVersion, cause.name(), reason.trim(), idempotencyKey, fingerprint) != 1) {
            return resolveFailedWrite(requestId, idempotencyKey, fingerprint, expectedVersion, true);
        }
        RoomReservationPort reservationOwner = roomPorts.getIfAvailable();
        if (reservationOwner != null && reservationOwner.hasImagingCapacityReservation(requestId)) {
            reservationOwner.releaseImagingCapacity(requestId, "DOCTOR_CANCELLED_BEFORE_FIRST_USE");
        }
        // A cancelled request must not leave its clinical order open, or the visit could never be closed.
        if (request.orderId() != null) {
            requiredOrderPort().cancelOrderIfOpen(request.orderId(), reason.trim());
        }
        DiagnosticRequestView result = toView(requests.find(requestId).orElseThrow(this::dependencyUnavailable));
        recordAudit(null, actor, request, operation, correlationId,
                Map.of("cause", cause.name(), "reason", reason.trim()));
        return result;
    }

    @Override
    @Transactional
    public DiagnosticRequestView transferRoom(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, UUID newRoomId, String reason, String correlationId) {
        requireDoctor(actor);
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        requireText(correlationId, "correlationId");
        String operation = DiagnosticAction.TRANSFER_ROOM.name();
        String fingerprint = fingerprint(newRoomId + "\n" + reason.trim() + "\n" + expectedVersion);
        ImagingExecution request = requests.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        VisitEvidence visit = requireDoctorVisit(actor, request);
        if (idempotencyKey.equals(request.transferIdempotencyKey())) return replay(request, request.transferFingerprint(), fingerprint);
        DiagnosticRequest domain = toDomain(request);
        authorization.requireAllowed(domain, DiagnosticAction.TRANSFER_ROOM, actor, evidence(request, visit), expectedVersion);
        if (newRoomId == null || newRoomId.equals(request.roomId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        UUID oldRoomId = request.roomId();
        RoomReservationPort reservationOwner = roomPorts.getIfAvailable();
        if (reservationOwner != null && reservationOwner.hasImagingCapacityReservation(requestId)) {
            // Preserve manual/legacy physical transfer semantics only when the owner proves a reservation exists.
            RoomEvidence room = requireRoomEvidence(newRoomId, request.imagingType());
            if (!room.operational() || !room.compatible() || !room.hasCapacity()) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
            }
            reservationOwner.releaseImagingCapacity(requestId, "ROOM_TRANSFERRED");
            RoomReservation reservation = reservationOwner.reserveImagingCapacity(requestId, newRoomId,
                    request.imagingType(), room.roomVersion());
            if (reservation == null || !reservation.reserved() || !requestId.equals(reservation.requestId())
                    || !newRoomId.equals(reservation.roomId()) || reservation.roomVersion() != room.roomVersion()) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
            }
        } else {
            var owner = queueRooms.getIfAvailable();
            if (owner == null) throw dependencyUnavailable();
            try {
                owner.requireEligibleRoom(newRoomId, request.serviceId(), request.imagingType());
            } catch (fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException invalidRoom) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
            }
        }
        if (requests.transferRoom(requestId, expectedVersion, newRoomId, idempotencyKey, fingerprint) != 1) {
            return resolveFailedWrite(requestId, idempotencyKey, fingerprint, expectedVersion, false);
        }
        // The patient's waiting entry in the room queue follows the request to its new room (UC-31 AF1).
        var roomTransfers = orderRoomTransfers == null ? null : orderRoomTransfers.getIfAvailable();
        if (roomTransfers != null) roomTransfers.followRoomChange(requestId, newRoomId);
        DiagnosticRequestView result = toView(requests.find(requestId).orElseThrow(this::dependencyUnavailable));
        recordAudit(null, actor, request, operation, correlationId,
                Map.of("oldRoomId", oldRoomId == null ? "UNASSIGNED" : oldRoomId.toString(), "newRoomId", newRoomId.toString(), "reason", reason.trim()));
        return result;
    }

    private VisitEvidence requireDoctorVisit(DiagnosticActor actor, ImagingExecution request) {
        VisitEvidence evidence = requiredVisitPort().requireImagingOrderEligibility(request.visitId(), actor.accountId());
        authorization.requireCreationAllowed(actor, request.visitId(), request.patientId(), evidence);
        if (!request.orderingDoctorAccountId().equals(evidence.treatingDoctorId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return evidence;
    }

    private DiagnosticEvidenceContext evidence(ImagingExecution request, VisitEvidence visit) {
        return new DiagnosticEvidenceContext(request.orderId(), request.visitId(), request.patientId(),
                visit.treatingDoctorId(), null, null, false, false, false, false, false);
    }

    private DiagnosticRequest toDomain(ImagingExecution request) {
        DiagnosticAttempt attempt = request.attemptId() == null ? null : new DiagnosticAttempt(request.attemptId(),
                request.orderId(), 1, request.firstUseEventId(), request.firstUseAt(), request.studyRef());
        return new DiagnosticRequest(request.orderId(), request.visitId(), request.patientId(),
                request.orderingDoctorAccountId(), request.serviceId(), request.imagingType(),
                DiagnosticRequestStatus.valueOf(request.status().name()), attempt, request.executionVersion(), request.createdAt());
    }

    private DiagnosticRequestView toView(ImagingExecution request) {
        return new DiagnosticRequestView(request.orderId(), request.visitId(), request.patientId(),
                request.orderingDoctorAccountId(), request.serviceId(), request.roomId(), request.imagingType(),
                request.bodyArea(), request.clinicalReason(), DiagnosticRequestStatus.valueOf(request.status().name()),
                request.cancellationCause(), request.cancellationReason(), request.studyRef(),
                request.executionVersion(), request.createdAt());
    }

    private void recordAudit(UUID eventId, DiagnosticActor actor, ImagingExecution request,
            String action, String correlationId, Map<String, Object> metadata) {
        var data = new LinkedHashMap<>(metadata);
        data.put("correlationRef", correlationId);
        if (eventId != null) data.put("eventId", eventId.toString());
        auditEvents.record(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC", action + ":ALLOWED", "DIAGNOSTIC_REQUEST")
                .entityId(request.orderId()).recordLabel(request.orderId().toString()).actorAccountId(actor.accountId())
                .actorType("ACCOUNT").metadata(data).outcome("ALLOWED").occurredAt(Instant.now()).build());
    }

    private DiagnosticRequestView replay(ImagingExecution request, String storedFingerprint, String fingerprint) {
        if (!Objects.equals(storedFingerprint, fingerprint)) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return toView(request);
    }

    private DiagnosticRequestView resolveFailedWrite(UUID requestId, String key, String fingerprint,
            long expectedVersion, boolean cancellation) {
        ImagingExecution current = requests.find(requestId).orElseThrow(this::dependencyUnavailable);
        String savedKey = cancellation ? current.cancelIdempotencyKey() : current.transferIdempotencyKey();
        String savedFingerprint = cancellation ? current.cancelFingerprint() : current.transferFingerprint();
        if (key.equals(savedKey)) return replay(current, savedFingerprint, fingerprint);
        if (current.executionVersion() != expectedVersion) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
        }
        throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
    }

    private RoomEvidence requireRoomEvidence(UUID roomId, String imagingType) {
        RoomEvidence evidence = requiredRoomPort().requireEligibleImagingRoom(roomId, imagingType);
        if (evidence == null) throw dependencyUnavailable();
        if (!roomId.equals(evidence.roomId()) || !imagingType.equalsIgnoreCase(evidence.imagingType())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        return evidence;
    }

    private boolean isAssignedQueueTechnician(DiagnosticActor actor, ImagingExecution request) {
        var rooms = queueRooms.getIfAvailable();
        if (rooms == null) throw dependencyUnavailable();
        if (request.roomId() == null || request.serviceId() == null || request.imagingType() == null) return false;
        try {
            rooms.requireEligibleRoom(request.roomId(), request.serviceId(), request.imagingType());
        } catch (fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException exception) {
            return false;
        }
        return rooms.isAssignedTechnician(actor.accountId(), request.roomId());
    }

    private VisitDiagnosticReadPort requiredVisitPort() {
        VisitDiagnosticReadPort port = visitPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        return port;
    }

    private RoomReservationPort requiredRoomPort() {
        RoomReservationPort port = roomPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        return port;
    }

    private ClinicalImagingOrderPort requiredOrderPort() {
        ClinicalImagingOrderPort port = orderPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        return port;
    }

    /** The order must exist, still be open, match the visit and service, and not already have a live request. */
    private void requireLinkableOrder(UUID serviceOrderId, UUID visitId, UUID serviceId) {
        ClinicalImagingOrder order = requiredOrderPort().requireOpenImagingOrder(serviceOrderId);
        if (order == null) throw new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
        if (!order.open() || !order.paid() || !visitId.equals(order.visitId()) || !serviceId.equals(order.serviceId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        if (requests.find(serviceOrderId).filter(r -> r.status() != ExecutionStatus.CANCELLED).isPresent()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
    }

    private ImagingServiceCatalogPort requiredCatalogPort() {
        ImagingServiceCatalogPort port = catalogPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        return port;
    }

    private void requireDoctor(DiagnosticActor actor) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.DOCTOR) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    private void requireImagingTechnician(DiagnosticActor actor) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER
                || actor.role() != AccountRole.IMAGING_TECHNICIAN) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte element : digest) hex.append(String.format("%02x", element));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID uuid(Map<String, Object> value, String field) {
        return UUID.fromString(string(value, field));
    }

    private String string(Map<String, Object> value, String field) {
        Object result = value.get(field);
        if (!(result instanceof String text)) throw dependencyUnavailable();
        return text;
    }

    private String nullableString(Map<String, Object> value, String field) {
        Object result = value.get(field);
        return result == null ? null : result instanceof String text ? text : throwDependency();
    }

    private String throwDependency() { throw dependencyUnavailable(); }

    private DiagnosticOperationException dependencyUnavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
