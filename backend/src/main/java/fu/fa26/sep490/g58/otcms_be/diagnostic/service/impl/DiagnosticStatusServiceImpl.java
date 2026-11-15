package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusResponsibility;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticPatientIdentity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
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
    private static final List<DiagnosticRequestStatus> TECHNICIAN_WORKLIST_STATUSES =
            List.of(DiagnosticRequestStatus.PENDING, DiagnosticRequestStatus.IN_PROGRESS,
                    DiagnosticRequestStatus.RESULT_AVAILABLE);

    private final ClinicalServiceOrderExecutionPort requests;
    private final ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts;
    private final ObjectProvider<RoomStatusAccessPort> roomPorts;
    private final PatientClinicalIdentityQueryService patientIdentities;

    public DiagnosticStatusServiceImpl(ClinicalServiceOrderExecutionPort requests,
            ObjectProvider<? extends VisitDiagnosticReadPort> visitPorts,
            ObjectProvider<RoomStatusAccessPort> roomPorts,
            PatientClinicalIdentityQueryService patientIdentities) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.roomPorts = Objects.requireNonNull(roomPorts, "roomPorts");
        this.patientIdentities = Objects.requireNonNull(patientIdentities, "patientIdentities");
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
        Page<ImagingExecution> result = requests.page(visitId, null, null, null, null, false, PageRequest.of(page, size));
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
        Page<ImagingExecution> batch;
        do {
            Pageable pageable = PageRequest.of(batchNumber++, SCAN_SIZE);
            batch = requests.page(null, null, null, roomId,
                    TECHNICIAN_WORKLIST_STATUSES.stream().map(s -> ExecutionStatus.valueOf(s.name())).toList(), false, pageable);
            for (ImagingExecution request : batch.getContent()) {
                if (request.roomId() == null) continue;
                boolean assigned = roomAssignments.computeIfAbsent(request.roomId(), ignored -> {
                    TechnicianRoomAssignment assignment = authorizeRoom(request.roomId(), actor.accountId());
                    return assignment.active() && request.roomId().equals(assignment.roomId())
                            && actor.accountId().equals(assignment.actorId());
                });
                if (!assigned) continue;
                if (authorizedCount >= first && authorizedCount < last) selected.add(toAssignedUploadWork(request));
                authorizedCount++;
            }
        } while (batch.hasNext());
        if (selected.isEmpty()) return new DiagnosticAssignedUploadWorkPage(selected, page, size, authorizedCount);
        var identities = patientIdentities.findClinicalIdentities(
                selected.stream().map(DiagnosticAssignedUploadWorkItem::patientProfileId).distinct().toList());
        List<DiagnosticAssignedUploadWorkItem> enriched = selected.stream().map(item -> {
            var identity = identities.get(item.patientProfileId());
            if (identity == null || !item.patientProfileId().equals(identity.patientProfileId())) throw unavailable();
            // Service name and technician instructions stay null until the clinical order port exposes them.
            return new DiagnosticAssignedUploadWorkItem(item.requestId(), item.attemptId(), item.visitId(),
                    item.patientProfileId(), new DiagnosticPatientIdentity(identity.patientProfileId(),
                            identity.fullName(), identity.dateOfBirth(), identity.gender()),
                    item.serviceOrderId(), item.currentStudyRef(), item.serviceId(), null, null,
                    item.imagingType(), item.bodyArea(), item.clinicalReason(), item.roomId(), item.status(),
                    item.version(), item.createdAt(),
                    item.updatedAt());
        }).toList();
        return new DiagnosticAssignedUploadWorkPage(enriched, page, size, authorizedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticOperationalStatus getStatus(DiagnosticActor actor, UUID requestId) {
        ImagingExecution request = requests.find(requestId).orElseThrow(this::unavailable);
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER) throw unavailable();
        if (actor.role() == AccountRole.IMAGING_TECHNICIAN) {
            if (request.roomId() == null) throw unavailable();
            if (!TECHNICIAN_WORKLIST_STATUSES.contains(DiagnosticRequestStatus.valueOf(request.status().name()))) throw unavailable();
            TechnicianRoomAssignment assignment = authorizeRoom(request.roomId(), actor.accountId());
            if (!assignment.active() || !request.roomId().equals(assignment.roomId())
                    || !actor.accountId().equals(assignment.actorId())) throw unavailable();
        } else {
            VisitStatusResponsibility responsibility = responsibility(actor);
            VisitStatusAuthorization authorization = authorizeVisit(request.visitId(), actor, responsibility);
            if (!authorization.authorized() || !request.visitId().equals(authorization.visitId())
                    || !actor.accountId().equals(authorization.actorId())
                    || responsibility != authorization.responsibility()) throw unavailable();
        }
        return toStatus(request);
    }

    private VisitStatusAuthorization authorizeVisit(UUID visitId, DiagnosticActor actor,
            VisitStatusResponsibility responsibility) {
        if (visitId == null || actor == null || actor.kind() != DiagnosticActor.Kind.USER) throw unavailable();
        VisitDiagnosticReadPort port = visitPorts.getIfAvailable();
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

    private DiagnosticOperationalStatus toStatus(ImagingExecution request) {
        return new DiagnosticOperationalStatus(request.orderId(), request.visitId(), request.imagingType(),
                request.roomId(), DiagnosticRequestStatus.valueOf(request.status().name()), request.executionVersion(), request.createdAt(),
                request.updatedAt());
    }

    private DiagnosticAssignedUploadWorkItem toAssignedUploadWork(ImagingExecution request) {
        return new DiagnosticAssignedUploadWorkItem(request.orderId(), request.attemptId(),
                request.visitId(), request.patientId(), null, request.orderId(),
                request.studyRef(), request.serviceId(), null, null, request.imagingType(), request.bodyArea(),
                request.clinicalReason(), request.roomId(),
                DiagnosticRequestStatus.valueOf(request.status().name()), request.executionVersion(), request.createdAt(), request.updatedAt());
    }

    private DiagnosticOperationException unavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
    }
}
