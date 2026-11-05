package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

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
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAuditEventJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticIdempotencyRecordJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
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

    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticAttemptJpaRepository attempts;
    private final DiagnosticIdempotencyRecordJpaRepository idempotency;
    private final DiagnosticAuditEventJpaRepository auditEvents;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts;
    private final ObjectProvider<RoomReservationPort> roomPorts;
    private final ObjectProvider<ImagingServiceCatalogPort> catalogPorts;
    private final ObjectProvider<ClinicalImagingOrderPort> orderPorts;

    public DiagnosticRequestServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts,
            DiagnosticIdempotencyRecordJpaRepository idempotency,
            DiagnosticAuditEventJpaRepository auditEvents,
            DiagnosticAuthorizationPolicy authorization,
            ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts,
            ObjectProvider<RoomReservationPort> roomPorts,
            ObjectProvider<ImagingServiceCatalogPort> catalogPorts,
            ObjectProvider<ClinicalImagingOrderPort> orderPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.roomPorts = Objects.requireNonNull(roomPorts, "roomPorts");
        this.catalogPorts = Objects.requireNonNull(catalogPorts, "catalogPorts");
        this.orderPorts = Objects.requireNonNull(orderPorts, "orderPorts");
    }

    @Override
    @Transactional
    public DiagnosticRequestView create(DiagnosticActor actor, UUID visitId, String idempotencyKey,
            UUID serviceId, String imagingType, String bodyArea, String clinicalReason,
            UUID roomId, UUID serviceOrderId, String correlationId) {
        requireDoctor(actor);
        requireText(idempotencyKey, "idempotencyKey");
        requireText(correlationId, "correlationId");
        String modality = requireText(imagingType, "imagingType");
        String area = requireText(bodyArea, "bodyArea");
        String reason = requireText(clinicalReason, "clinicalReason");
        UUID operationId = UUID.randomUUID();
        String fingerprint = fingerprint(serviceId + "\n" + modality + "\n" + area + "\n" + reason + "\n" + roomId
                + "\n" + serviceOrderId);
        int claimed = idempotency.claimActorOperation(operationId, actor.accountId(), visitId, null,
                CREATE_OPERATION, idempotencyKey, fingerprint);
        if (claimed == 0) {
            DiagnosticIdempotencyRecordJpaEntity saved = idempotency.findActorOperation(actor.accountId(),
                    visitId, null, CREATE_OPERATION, idempotencyKey).orElseThrow(this::dependencyUnavailable);
            return replay(saved, fingerprint);
        }

        VisitEvidence visitEvidence = requiredVisitPort().requireImagingOrderEligibility(visitId, actor.accountId());
        UUID patientId = visitEvidence == null ? null : visitEvidence.patientId();
        authorization.requireCreationAllowed(actor, visitId, patientId, visitEvidence);
        ImagingService service = requiredCatalogPort().requireImagingService(serviceId, modality);
        if (service == null || !service.active() || !serviceId.equals(service.serviceId())
                || !modality.equalsIgnoreCase(service.imagingType())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        }
        if (serviceOrderId != null) requireLinkableOrder(serviceOrderId, visitId, serviceId);
        RoomEvidence room = requireRoomEvidence(roomId, modality);
        if (!room.operational() || !room.compatible() || !room.hasCapacity()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
        }

        UUID requestId = UUID.randomUUID();
        DiagnosticRequestJpaEntity request = new DiagnosticRequestJpaEntity(requestId, visitId,
                visitEvidence.patientId(), actor.accountId(), serviceId, roomId, modality, area, reason,
                DiagnosticRequestStatus.PENDING, null, null, null);
        request.setCreatedAt(Instant.now());
        request.setServiceOrderId(serviceOrderId);
        requests.saveAndFlush(request);

        RoomReservation reservation = requiredRoomPort().reserveImagingCapacity(
                requestId, roomId, modality, room.roomVersion());
        if (reservation == null || !reservation.reserved() || !requestId.equals(reservation.requestId())
                || !roomId.equals(reservation.roomId()) || reservation.roomVersion() != room.roomVersion()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
        }

        DiagnosticRequestView result = toView(request);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("roomId", roomId.toString());
        auditDetails.put("serviceId", serviceId.toString());
        if (serviceOrderId != null) auditDetails.put("serviceOrderId", serviceOrderId.toString());
        recordAudit(null, actor, request, "CREATE_REQUEST", correlationId, auditDetails);
        saveReplayResult(actor, visitId, null, CREATE_OPERATION, idempotencyKey, result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticRequestPage listVisitRequests(DiagnosticActor actor, UUID visitId, int page, int size) {
        requireDoctor(actor);
        requirePage(page, size);
        Page<DiagnosticRequestJpaEntity> result = requests.findAllByVisitIdAndOrderingDoctorIdOrderByCreatedAtDescIdDesc(
                visitId, actor.accountId(), PageRequest.of(page, size));
        return new DiagnosticRequestPage(result.getContent().stream().map(this::toView).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticRequestView get(DiagnosticActor actor, UUID requestId) {
        requireDoctor(actor);
        DiagnosticRequestJpaEntity entity = requests.findById(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (!actor.accountId().equals(entity.getOrderingDoctorId())) {
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
        Page<DiagnosticRequestJpaEntity> batch;
        Map<String, RoomEvidence> roomCache = new LinkedHashMap<>();
        do {
            Pageable pageable = PageRequest.of(batchNumber++, WORK_SCAN_SIZE);
            batch = requests.findAllByStatusInOrderByCreatedAtAscIdAsc(UPLOAD_STATUSES, pageable);
            for (DiagnosticRequestJpaEntity request : batch.getContent()) {
                if (request.getRoomId() == null) continue;
                if (roomId != null && !roomId.equals(request.getRoomId())) continue;
                String cacheKey = request.getRoomId() + "|" + request.getImagingType();
                RoomEvidence room = roomCache.computeIfAbsent(cacheKey,
                        ignored -> requireRoomEvidence(request.getRoomId(), request.getImagingType()));
                if (room.roomId() == null || !request.getRoomId().equals(room.roomId())) {
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
        DiagnosticRequestJpaEntity request = requests.findById(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD));
        if (!UPLOAD_STATUSES.contains(request.getStatus()) || !attemptId.equals(request.getCurrentAttemptId())
                || attempts.findByRequestIdAndId(requestId, attemptId).isEmpty()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        RoomEvidence room = requireRoomEvidence(request.getRoomId(), request.getImagingType());
        if (room.roomId() == null || !request.getRoomId().equals(room.roomId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        boolean assigned = room.operational() && room.compatible()
                && actor.accountId().equals(room.assignedTechnicianId());
        if (!assigned) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return new DiagnosticAssignedImagingWork(requestId, attemptId, request.getVisitId(),
                request.getPatientId(), Long.toString(room.roomVersion()), true);
    }

    @Override
    @Transactional
    public DiagnosticRequestView cancel(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, CancellationCause cause, String reason, String correlationId) {
        requireDoctor(actor);
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        requireText(correlationId, "correlationId");
        DiagnosticRequestJpaEntity preliminary = requests.findById(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        String operation = DiagnosticAction.MANUAL_CANCEL.name();
        String fingerprint = fingerprint(cause.name() + "\n" + reason.trim() + "\n" + expectedVersion);
        int claimed = idempotency.claimActorOperation(UUID.randomUUID(), actor.accountId(),
                preliminary.getVisitId(), requestId, operation, idempotencyKey, fingerprint);
        if (claimed == 0) return replay(idempotency.findActorOperation(actor.accountId(), preliminary.getVisitId(),
                requestId, operation, idempotencyKey).orElseThrow(this::dependencyUnavailable), fingerprint);

        DiagnosticRequestJpaEntity request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        VisitEvidence visit = requireDoctorVisit(actor, request);
        DiagnosticRequest domain = toDomain(request);
        DiagnosticEvidenceContext evidence = evidence(request, visit);
        authorization.requireAllowed(domain, DiagnosticAction.MANUAL_CANCEL, actor, evidence, expectedVersion);

        request.setStatus(DiagnosticRequestStatus.CANCELLED);
        request.setCancellationCause(cause.name());
        request.setCancellationReason(reason.trim());
        requests.saveAndFlush(request);
        if (request.getRoomId() != null) requiredRoomPort().releaseImagingCapacity(requestId, "DOCTOR_CANCELLED_BEFORE_FIRST_USE");
        // A cancelled request must not leave its clinical order open, or the visit could never be closed.
        if (request.getServiceOrderId() != null) {
            requiredOrderPort().cancelOrderIfOpen(request.getServiceOrderId(), reason.trim());
        }
        DiagnosticRequestView result = toView(request);
        recordAudit(null, actor, request, operation, correlationId,
                Map.of("cause", cause.name(), "reason", reason.trim()));
        saveReplayResult(actor, request.getVisitId(), requestId, operation, idempotencyKey, result);
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
        DiagnosticRequestJpaEntity preliminary = requests.findById(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        String operation = DiagnosticAction.TRANSFER_ROOM.name();
        String fingerprint = fingerprint(newRoomId + "\n" + reason.trim() + "\n" + expectedVersion);
        int claimed = idempotency.claimActorOperation(UUID.randomUUID(), actor.accountId(),
                preliminary.getVisitId(), requestId, operation, idempotencyKey, fingerprint);
        if (claimed == 0) return replay(idempotency.findActorOperation(actor.accountId(), preliminary.getVisitId(),
                requestId, operation, idempotencyKey).orElseThrow(this::dependencyUnavailable), fingerprint);

        DiagnosticRequestJpaEntity request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        VisitEvidence visit = requireDoctorVisit(actor, request);
        DiagnosticRequest domain = toDomain(request);
        authorization.requireAllowed(domain, DiagnosticAction.TRANSFER_ROOM, actor, evidence(request, visit), expectedVersion);
        if (newRoomId.equals(request.getRoomId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        RoomEvidence room = requireRoomEvidence(newRoomId, request.getImagingType());
        if (!room.operational() || !room.compatible() || !room.hasCapacity()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
        }
        UUID oldRoomId = request.getRoomId();
        if (oldRoomId != null) requiredRoomPort().releaseImagingCapacity(requestId, "ROOM_TRANSFERRED");
        RoomReservation reservation = requiredRoomPort().reserveImagingCapacity(
                requestId, newRoomId, request.getImagingType(), room.roomVersion());
        if (reservation == null || !reservation.reserved() || !requestId.equals(reservation.requestId())
                || !newRoomId.equals(reservation.roomId()) || reservation.roomVersion() != room.roomVersion()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);
        }
        request.setRoomId(newRoomId);
        requests.saveAndFlush(request);
        DiagnosticRequestView result = toView(request);
        recordAudit(null, actor, request, operation, correlationId,
                Map.of("oldRoomId", oldRoomId == null ? "UNASSIGNED" : oldRoomId.toString(), "newRoomId", newRoomId.toString(), "reason", reason.trim()));
        saveReplayResult(actor, request.getVisitId(), requestId, operation, idempotencyKey, result);
        return result;
    }

    private VisitEvidence requireDoctorVisit(DiagnosticActor actor, DiagnosticRequestJpaEntity request) {
        VisitEvidence evidence = requiredVisitPort().requireImagingOrderEligibility(request.getVisitId(), actor.accountId());
        authorization.requireCreationAllowed(actor, request.getVisitId(), request.getPatientId(), evidence);
        if (!request.getOrderingDoctorId().equals(evidence.treatingDoctorId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return evidence;
    }

    private DiagnosticEvidenceContext evidence(DiagnosticRequestJpaEntity request, VisitEvidence visit) {
        return new DiagnosticEvidenceContext(request.getId(), request.getVisitId(), request.getPatientId(),
                visit.treatingDoctorId(), null, null, false, false, false, false, false);
    }

    private DiagnosticRequest toDomain(DiagnosticRequestJpaEntity entity) {
        return new DiagnosticRequest(entity.getId(), entity.getVisitId(), entity.getPatientId(),
                entity.getOrderingDoctorId(), entity.getServiceId(), entity.getImagingType(),
                entity.getStatus(), null, entity.getVersion(), entity.getCreatedAt());
    }

    private DiagnosticRequestView toView(DiagnosticRequestJpaEntity entity) {
        String studyRef = entity.getCurrentAttemptId() == null ? null
                : attempts.findByRequestIdAndId(entity.getId(), entity.getCurrentAttemptId())
                        .orElseThrow(this::dependencyUnavailable).getStudyRef();
        return new DiagnosticRequestView(entity.getId(), entity.getVisitId(), entity.getPatientId(),
                entity.getOrderingDoctorId(), entity.getServiceId(), entity.getRoomId(), entity.getImagingType(),
                entity.getBodyArea(), entity.getClinicalReason(), entity.getStatus(),
                entity.getCancellationCause(), entity.getCancellationReason(), studyRef,
                entity.getVersion(), entity.getCreatedAt());
    }

    private void recordAudit(UUID eventId, DiagnosticActor actor, DiagnosticRequestJpaEntity request,
            String action, String correlationId, Map<String, Object> metadata) {
        auditEvents.save(new DiagnosticAuditEventJpaEntity(UUID.randomUUID(), eventId, actor.accountId(), null,
                request.getId(), request.getVisitId(), action, DiagnosticOperationOutcome.ALLOWED.name(),
                Instant.now(), correlationId, metadata));
    }

    private void saveReplayResult(DiagnosticActor actor, UUID visitId, UUID requestId,
            String operation, String key, DiagnosticRequestView view) {
        DiagnosticIdempotencyRecordJpaEntity record = idempotency.findActorOperation(actor.accountId(),
                visitId, requestId, operation, key).orElseThrow(this::dependencyUnavailable);
        if (idempotency.completeResult(record.getId(), snapshot(view)) != 1) throw dependencyUnavailable();
    }

    private DiagnosticRequestView replay(DiagnosticIdempotencyRecordJpaEntity saved, String fingerprint) {
        if (!saved.getPayloadFingerprint().equals(fingerprint)) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        if (saved.getResultPayload() == null || saved.getResultPayload().isEmpty()) throw dependencyUnavailable();
        Map<String, Object> value = saved.getResultPayload();
        return new DiagnosticRequestView(uuid(value, "requestId"), uuid(value, "visitId"), uuid(value, "patientId"),
                uuid(value, "orderingDoctorId"), uuid(value, "serviceId"),
                nullableString(value, "roomId") == null ? null : UUID.fromString(nullableString(value, "roomId")),
                string(value, "imagingType"), string(value, "bodyArea"), string(value, "clinicalReason"),
                DiagnosticRequestStatus.valueOf(string(value, "status")), nullableString(value, "cancellationCause"),
                nullableString(value, "cancellationReason"), nullableString(value, "currentStudyRef"),
                ((Number) value.get("version")).longValue(), Instant.parse(string(value, "createdAt")));
    }

    private Map<String, Object> snapshot(DiagnosticRequestView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", view.requestId().toString());
        result.put("visitId", view.visitId().toString());
        result.put("patientId", view.patientId().toString());
        result.put("orderingDoctorId", view.orderingDoctorId().toString());
        result.put("serviceId", view.serviceId().toString());
        result.put("roomId", view.roomId() == null ? null : view.roomId().toString());
        result.put("imagingType", view.imagingType());
        result.put("bodyArea", view.bodyArea());
        result.put("clinicalReason", view.clinicalReason());
        result.put("status", view.status().name());
        result.put("cancellationCause", view.cancellationCause());
        result.put("cancellationReason", view.cancellationReason());
        result.put("currentStudyRef", view.currentStudyRef());
        result.put("version", view.version());
        result.put("createdAt", view.createdAt().toString());
        return result;
    }

    private RoomEvidence requireRoomEvidence(UUID roomId, String imagingType) {
        RoomEvidence evidence = requiredRoomPort().requireEligibleImagingRoom(roomId, imagingType);
        if (evidence == null) throw dependencyUnavailable();
        if (!roomId.equals(evidence.roomId()) || !imagingType.equalsIgnoreCase(evidence.imagingType())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        return evidence;
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
        if (!order.open() || !visitId.equals(order.visitId()) || !serviceId.equals(order.serviceId())) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        if (requests.existsByServiceOrderIdAndStatusNot(serviceOrderId, DiagnosticRequestStatus.CANCELLED)) {
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
