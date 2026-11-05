package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusResponsibility;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import java.util.ArrayList;
import java.util.HashMap;
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
public class DiagnosticStatusServiceImpl implements DiagnosticStatusService {
    private static final int MAX_SIZE = 100;
    private static final int SCAN_SIZE = 100;
    private static final List<DiagnosticRequestStatus> ACTIVE_UPLOAD_STATUSES =
            List.of(DiagnosticRequestStatus.PENDING, DiagnosticRequestStatus.IN_PROGRESS);

    private final DiagnosticRequestJpaRepository requests;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<RoomStatusAccessPort> roomPorts;

    public DiagnosticStatusServiceImpl(DiagnosticRequestJpaRepository requests,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<RoomStatusAccessPort> roomPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.roomPorts = Objects.requireNonNull(roomPorts, "roomPorts");
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticOperationalStatusPage listVisitStatus(DiagnosticActor actor, UUID visitId, int page, int size) {
        requirePage(page, size);
        VisitStatusResponsibility responsibility = responsibility(actor);
        VisitStatusAuthorization authorization = authorizeVisit(visitId, actor, responsibility);
        if (!authorization.authorized() || !visitId.equals(authorization.visitId())
                || !actor.accountId().equals(authorization.actorId())
                || responsibility != authorization.responsibility()) throw unavailable();
        Page<DiagnosticRequestJpaEntity> result = requests.findAllByVisitIdOrderByCreatedAtDescIdDesc(
                visitId, PageRequest.of(page, size));
        return new DiagnosticOperationalStatusPage(result.getContent().stream().map(this::toStatus).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticAssignedUploadWorkPage listTechnicianUploadWork(
            DiagnosticActor actor, UUID roomId, int page, int size) {
        requirePage(page, size);
        requireTechnician(actor);
        long first = (long) page * size;
        long last = first + size;
        List<DiagnosticAssignedUploadWorkItem> selected = new ArrayList<>(size);
        Map<UUID, Boolean> roomAssignments = new HashMap<>();
        int batchNumber = 0;
        long authorizedCount = 0;
        Page<DiagnosticRequestJpaEntity> batch;
        do {
            Pageable pageable = PageRequest.of(batchNumber++, SCAN_SIZE);
            batch = roomId == null
                    ? requests.findAllByStatusInOrderByCreatedAtDescIdDesc(ACTIVE_UPLOAD_STATUSES, pageable)
                    : requests.findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
                            roomId, ACTIVE_UPLOAD_STATUSES, pageable);
            for (DiagnosticRequestJpaEntity request : batch.getContent()) {
                boolean assigned = roomAssignments.computeIfAbsent(request.getRoomId(), ignored -> {
                    TechnicianRoomAssignment assignment = authorizeRoom(request.getRoomId(), actor.accountId());
                    return assignment.active() && request.getRoomId().equals(assignment.roomId())
                            && actor.accountId().equals(assignment.actorId());
                });
                if (!assigned) continue;
                if (authorizedCount >= first && authorizedCount < last) selected.add(toAssignedUploadWork(request));
                authorizedCount++;
            }
        } while (batch.hasNext());
        return new DiagnosticAssignedUploadWorkPage(selected, page, size, authorizedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticOperationalStatus getStatus(DiagnosticActor actor, UUID requestId) {
        DiagnosticRequestJpaEntity request = requests.findById(requestId).orElseThrow(this::unavailable);
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER) throw unavailable();
        if (actor.role() == AccountRole.IMAGING_TECHNICIAN) {
            if (!ACTIVE_UPLOAD_STATUSES.contains(request.getStatus())) throw unavailable();
            TechnicianRoomAssignment assignment = authorizeRoom(request.getRoomId(), actor.accountId());
            if (!assignment.active() || !request.getRoomId().equals(assignment.roomId())
                    || !actor.accountId().equals(assignment.actorId())) throw unavailable();
        } else {
            VisitStatusResponsibility responsibility = responsibility(actor);
            VisitStatusAuthorization authorization = authorizeVisit(request.getVisitId(), actor, responsibility);
            if (!authorization.authorized() || !request.getVisitId().equals(authorization.visitId())
                    || !actor.accountId().equals(authorization.actorId())
                    || responsibility != authorization.responsibility()) throw unavailable();
        }
        return toStatus(request);
    }

    private VisitStatusAuthorization authorizeVisit(UUID visitId, DiagnosticActor actor,
            VisitStatusResponsibility responsibility) {
        if (visitId == null || actor == null || actor.kind() != DiagnosticActor.Kind.USER) throw unavailable();
        VisitDiagnosticPort port = visitPorts.getIfAvailable();
        if (port == null) throw unavailable();
        try {
            VisitStatusAuthorization authorization = port.authorizeImagingStatus(visitId, actor.accountId(), responsibility);
            if (authorization == null) throw unavailable();
            return authorization;
        } catch (DiagnosticOperationException exception) {
            if (exception.outcome() == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                    || exception.outcome() == DiagnosticOperationOutcome.RECORD_NOT_FOUND) throw unavailable();
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private TechnicianRoomAssignment authorizeRoom(UUID roomId, UUID actorId) {
        RoomStatusAccessPort port = roomPorts.getIfAvailable();
        if (port == null) throw unavailable();
        try {
            TechnicianRoomAssignment assignment = port.authorizeAssignedTechnician(roomId, actorId);
            if (assignment == null) throw unavailable();
            return assignment;
        } catch (DiagnosticOperationException exception) {
            if (exception.outcome() == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                    || exception.outcome() == DiagnosticOperationOutcome.RECORD_NOT_FOUND) throw unavailable();
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private VisitStatusResponsibility responsibility(DiagnosticActor actor) {
        if (actor != null && actor.kind() == DiagnosticActor.Kind.USER) {
            if (actor.role() == AccountRole.DOCTOR) return VisitStatusResponsibility.DOCTOR;
            if (actor.role() == AccountRole.RECEPTIONIST) return VisitStatusResponsibility.RECEPTIONIST;
        }
        throw unavailable();
    }

    private void requireTechnician(DiagnosticActor actor) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER
                || actor.role() != AccountRole.IMAGING_TECHNICIAN) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) throw unavailable();
    }

    private DiagnosticOperationalStatus toStatus(DiagnosticRequestJpaEntity request) {
        return new DiagnosticOperationalStatus(request.getId(), request.getVisitId(), request.getImagingType(),
                request.getRoomId(), request.getStatus(), request.getVersion(), request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private DiagnosticAssignedUploadWorkItem toAssignedUploadWork(DiagnosticRequestJpaEntity request) {
        return new DiagnosticAssignedUploadWorkItem(request.getId(), request.getCurrentAttemptId(),
                request.getVisitId(), request.getImagingType(), request.getRoomId(), request.getStatus(),
                request.getVersion(), request.getCreatedAt(), request.getUpdatedAt());
    }

    private DiagnosticOperationException unavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
    }
}
