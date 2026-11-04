package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusResponsibility;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticStatusServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DiagnosticStatusT015Test {
    private final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    private final ObjectProvider<VisitDiagnosticPort> visitPorts = mock(ObjectProvider.class);
    private final ObjectProvider<RoomStatusAccessPort> roomPorts = mock(ObjectProvider.class);
    private final VisitDiagnosticPort visitPort = mock(VisitDiagnosticPort.class);
    private final RoomStatusAccessPort roomPort = mock(RoomStatusAccessPort.class);
    private final DiagnosticStatusServiceImpl service = new DiagnosticStatusServiceImpl(requests, visitPorts, roomPorts);

    @BeforeEach
    void configurePorts() {
        when(visitPorts.getIfAvailable()).thenReturn(visitPort);
        when(roomPorts.getIfAvailable()).thenReturn(roomPort);
    }

    @Test
    void receptionistStatusListRequiresVisitResponsibilityAndReturnsOperationalProjection() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.RECEPTIONIST);
        DiagnosticRequestJpaEntity request = request(visitId, UUID.randomUUID());
        when(visitPort.authorizeImagingStatus(visitId, actorId, VisitStatusResponsibility.RECEPTIONIST))
                .thenReturn(new VisitStatusAuthorization(visitId, actorId,
                        VisitStatusResponsibility.RECEPTIONIST, true));
        when(requests.findAllByVisitIdOrderByCreatedAtDescIdDesc(eq(visitId), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));

        var result = service.listVisitStatus(actor, visitId, 0, 20);

        assertEquals(1, result.totalItems());
        assertEquals(request.getId(), result.items().getFirst().requestId());
        assertEquals(visitId, result.items().getFirst().visitId());
        assertEquals("MRI", result.items().getFirst().imagingType());
        assertEquals(request.getRoomId(), result.items().getFirst().roomId());
        assertEquals(request.getVersion(), result.items().getFirst().version());
    }

    @Test
    void doctorWithoutOwnerResponsibilityCannotListStatus() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        when(visitPort.authorizeImagingStatus(visitId, actorId, VisitStatusResponsibility.DOCTOR))
                .thenReturn(new VisitStatusAuthorization(visitId, actorId,
                        VisitStatusResponsibility.DOCTOR, false));

        assertThrows(DiagnosticOperationException.class, () -> service.listVisitStatus(actor, visitId, 0, 20));
        verifyNoInteractions(requests);
    }

    @Test
    void technicianSeesOnlyAssignedActiveRoomWork() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.IMAGING_TECHNICIAN);
        DiagnosticRequestJpaEntity request = request(visitId, roomId);
        when(roomPort.authorizeAssignedTechnician(roomId, actorId))
                .thenReturn(new TechnicianRoomAssignment(roomId, actorId, true));
        when(requests.findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(eq(roomId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 100), 1));

        var result = service.listTechnicianUploadWork(actor, roomId, 0, 20);

        assertEquals(1, result.totalItems());
        assertEquals(roomId, result.items().getFirst().roomId());
        assertEquals(DiagnosticRequestStatus.PENDING, result.items().getFirst().status());
    }

    @Test
    void missingAndOutOfScopeSingleStatusReadsHaveSameUnavailableOutcome() {
        UUID actorId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        UUID visitId = UUID.randomUUID();
        DiagnosticRequestJpaEntity request = request(visitId, UUID.randomUUID());
        UUID requestId = request.getId();
        when(requests.findById(requestId)).thenReturn(java.util.Optional.empty());
        DiagnosticOperationException missing = assertThrows(DiagnosticOperationException.class,
                () -> service.getStatus(actor, requestId));

        when(requests.findById(requestId)).thenReturn(java.util.Optional.of(request));
        when(visitPort.authorizeImagingStatus(eq(visitId), eq(actorId), eq(VisitStatusResponsibility.DOCTOR)))
                .thenReturn(new VisitStatusAuthorization(visitId, actorId, VisitStatusResponsibility.DOCTOR, false));
        DiagnosticOperationException denied = assertThrows(DiagnosticOperationException.class,
                () -> service.getStatus(actor, requestId));

        assertEquals(missing.outcome(), denied.outcome());
        assertEquals(fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.RECORD_NOT_FOUND,
                denied.outcome());
    }

    private DiagnosticRequestJpaEntity request(UUID visitId, UUID roomId) {
        DiagnosticRequestJpaEntity request = new DiagnosticRequestJpaEntity(UUID.randomUUID(), visitId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roomId, "MRI", "BRAIN", "Clinical reason",
                DiagnosticRequestStatus.PENDING, null, null, null);
        request.setCreatedAt(Instant.parse("2026-11-01T10:00:00Z"));
        return request;
    }
}
