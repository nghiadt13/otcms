package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedImagingWork;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticRequestServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DiagnosticAssignedImagingWorkAuthorizationTest {
    @Test
    void returnsOwnerIdsOnlyForExactCurrentAttemptAndActiveRoomAssignment() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        var request = execution(requestId, visitId, patientId, roomId, attemptId);
        var requests = mock(ClinicalServiceOrderExecutionPort.class);
        var rooms = mock(RoomReservationPort.class);
        when(requests.find(requestId)).thenReturn(Optional.of(request));
        when(rooms.requireEligibleImagingRoom(roomId, "CT"))
                .thenReturn(new RoomEvidence(roomId, "CT", true, true, true, actorId, 9));
        DiagnosticRequestServiceImpl service = service(requests, rooms);

        DiagnosticAssignedImagingWork assigned = service.requireAssignedImagingWork(
                DiagnosticActor.user(actorId, AccountRole.IMAGING_TECHNICIAN), requestId, attemptId);

        assertEquals(requestId, assigned.requestId());
        assertEquals(attemptId, assigned.attemptId());
        assertEquals(visitId, assigned.visitId());
        assertEquals(patientId, assigned.patientId());
        assertEquals("9", assigned.assignmentVersion());
        assertEquals(true, assigned.currentlyAssigned());
    }

    @Test
    void hidesWorkWhenAttemptIsNotCurrentOrTechnicianIsNotAssigned() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID currentAttemptId = UUID.randomUUID();
        var request = execution(requestId, UUID.randomUUID(), UUID.randomUUID(), roomId, currentAttemptId);
        var requests = mock(ClinicalServiceOrderExecutionPort.class);
        var rooms = mock(RoomReservationPort.class);
        when(requests.find(requestId)).thenReturn(Optional.of(request));
        DiagnosticRequestServiceImpl service = service(requests, rooms);
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.IMAGING_TECHNICIAN);

        DiagnosticOperationException staleAttempt = assertThrows(DiagnosticOperationException.class,
                () -> service.requireAssignedImagingWork(actor, requestId, attemptId));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, staleAttempt.outcome());

        when(rooms.requireEligibleImagingRoom(roomId, "CT"))
                .thenReturn(new RoomEvidence(roomId, "CT", true, true, true, UUID.randomUUID(), 9));
        DiagnosticOperationException wrongTechnician = assertThrows(DiagnosticOperationException.class,
                () -> service.requireAssignedImagingWork(actor, requestId, currentAttemptId));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, wrongTechnician.outcome());
    }

    @SuppressWarnings("unchecked")
    private DiagnosticRequestServiceImpl service(ClinicalServiceOrderExecutionPort requests, RoomReservationPort roomPort) {
        var audits = mock(AuditLogWriter.class);
        var authorization = mock(DiagnosticAuthorizationPolicy.class);
        var visitPorts = (ObjectProvider<? extends VisitDiagnosticReadPort>) mock(ObjectProvider.class);
        var roomPorts = (ObjectProvider<RoomReservationPort>) mock(ObjectProvider.class);
        var catalogPorts = (ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort>) mock(ObjectProvider.class);
        var orderPorts = (ObjectProvider<ClinicalImagingOrderPort>) mock(ObjectProvider.class);
        when(roomPorts.getIfAvailable()).thenReturn(roomPort);
        return new DiagnosticRequestServiceImpl(requests, audits, authorization,
                visitPorts, roomPorts, catalogPorts, orderPorts, (ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService>) mock(ObjectProvider.class),
                (ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService>) mock(ObjectProvider.class));
    }

    private static ClinicalServiceOrderExecutionPort.ImagingExecution execution(UUID id, UUID visit, UUID patient,
            UUID room, UUID attempt) {
        return new ClinicalServiceOrderExecutionPort.ImagingExecution(id, visit, patient, UUID.randomUUID(),
                UUID.randomUUID(), room, "CT", null, null, ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS,
                null, null, attempt, null, null, null, null, null, null, null, null, null, null, null, null,
                0, null, null);
    }
}
