package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedImagingWork;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAuditEventJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticIdempotencyRecordJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticRequestServiceImpl;
import java.time.Instant;
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
        var request = new DiagnosticRequestJpaEntity(requestId, visitId, patientId, UUID.randomUUID(),
                UUID.randomUUID(), roomId, "CT", DiagnosticRequestStatus.IN_PROGRESS, null, null, attemptId);
        var attempt = new DiagnosticAttemptJpaEntity(attemptId, requestId, 1, UUID.randomUUID(),
                "ACQUISITION_STARTED", "fixture", actorId, Instant.parse("2026-11-05T00:00:00Z"), null);
        var requests = mock(DiagnosticRequestJpaRepository.class);
        var attempts = mock(DiagnosticAttemptJpaRepository.class);
        var rooms = mock(RoomReservationPort.class);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(attempts.findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.of(attempt));
        when(rooms.requireEligibleImagingRoom(roomId, "CT"))
                .thenReturn(new RoomEvidence(roomId, "CT", true, true, true, actorId, 9));
        DiagnosticRequestServiceImpl service = service(requests, attempts, rooms);

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
        var request = new DiagnosticRequestJpaEntity(requestId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), roomId, "CT", DiagnosticRequestStatus.IN_PROGRESS,
                null, null, UUID.randomUUID());
        var requests = mock(DiagnosticRequestJpaRepository.class);
        var attempts = mock(DiagnosticAttemptJpaRepository.class);
        var rooms = mock(RoomReservationPort.class);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(attempts.findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.empty());
        DiagnosticRequestServiceImpl service = service(requests, attempts, rooms);
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.IMAGING_TECHNICIAN);

        DiagnosticOperationException staleAttempt = assertThrows(DiagnosticOperationException.class,
                () -> service.requireAssignedImagingWork(actor, requestId, attemptId));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, staleAttempt.outcome());

        when(attempts.findByRequestIdAndId(requestId, request.getCurrentAttemptId()))
                .thenReturn(Optional.of(mock(DiagnosticAttemptJpaEntity.class)));
        when(rooms.requireEligibleImagingRoom(roomId, "CT"))
                .thenReturn(new RoomEvidence(roomId, "CT", true, true, true, UUID.randomUUID(), 9));
        DiagnosticOperationException wrongTechnician = assertThrows(DiagnosticOperationException.class,
                () -> service.requireAssignedImagingWork(actor, requestId, request.getCurrentAttemptId()));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, wrongTechnician.outcome());
    }

    @SuppressWarnings("unchecked")
    private DiagnosticRequestServiceImpl service(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts, RoomReservationPort roomPort) {
        var idempotency = mock(DiagnosticIdempotencyRecordJpaRepository.class);
        var audits = mock(DiagnosticAuditEventJpaRepository.class);
        var authorization = mock(DiagnosticAuthorizationPolicy.class);
        var visitPorts = (ObjectProvider<? extends VisitDiagnosticReadPort>) mock(ObjectProvider.class);
        var roomPorts = (ObjectProvider<RoomReservationPort>) mock(ObjectProvider.class);
        var catalogPorts = (ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort>) mock(ObjectProvider.class);
        var orderPorts = (ObjectProvider<ClinicalImagingOrderPort>) mock(ObjectProvider.class);
        when(roomPorts.getIfAvailable()).thenReturn(roomPort);
        return new DiagnosticRequestServiceImpl(requests, attempts, idempotency, audits, authorization,
                visitPorts, roomPorts, catalogPorts, orderPorts, (ObjectProvider<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService>) mock(ObjectProvider.class));
    }
}
