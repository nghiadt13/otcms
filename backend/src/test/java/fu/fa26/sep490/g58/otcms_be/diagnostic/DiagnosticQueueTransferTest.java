package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class DiagnosticQueueTransferTest {
    final UUID id = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(),
            serviceId = UUID.randomUUID(), oldRoom = UUID.randomUUID(), newRoom = UUID.randomUUID();
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DiagnosticAttemptJpaRepository attempts = mock(DiagnosticAttemptJpaRepository.class);
    final DiagnosticIdempotencyRecordJpaRepository replay = mock(DiagnosticIdempotencyRecordJpaRepository.class);
    final DiagnosticAuditEventJpaRepository audits = mock(DiagnosticAuditEventJpaRepository.class);
    final RoomReservationPort capacity = mock(RoomReservationPort.class);
    final ImagingRoomQueueService queue = mock(ImagingRoomQueueService.class);
    final VisitDiagnosticReadPort visits = mock(VisitDiagnosticReadPort.class);
    final DiagnosticRequestJpaEntity row = new DiagnosticRequestJpaEntity(id, visit, patient, doctor, serviceId, oldRoom,
            "MRI", "KNEE", "Pain", DiagnosticRequestStatus.PENDING, null, null, null);
    DiagnosticRequestServiceImpl service;
    @BeforeEach void setup() {
        row.setCreatedAt(java.time.Instant.EPOCH);
        var beans = new StaticListableBeanFactory(); beans.addBean("capacity", capacity); beans.addBean("queue", queue); beans.addBean("visit", visits);
        service = new DiagnosticRequestServiceImpl(requests, attempts, replay, audits,
                new DiagnosticAuthorizationPolicyImpl(new DiagnosticTransitionPolicyImpl()), beans.getBeanProvider(VisitDiagnosticReadPort.class),
                beans.getBeanProvider(RoomReservationPort.class), beans.getBeanProvider(ImagingServiceCatalogPort.class),
                beans.getBeanProvider(ClinicalImagingOrderPort.class), beans.getBeanProvider(ImagingRoomQueueService.class));
        when(requests.findById(id)).thenReturn(Optional.of(row)); when(requests.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(visits.requireImagingOrderEligibility(eq(visit), any())).thenReturn(new VisitEvidence(visit, patient, doctor, true, true, true));
        when(replay.claimActorOperation(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        var saved = mock(DiagnosticIdempotencyRecordJpaEntity.class); when(saved.getId()).thenReturn(UUID.randomUUID());
        when(replay.findActorOperation(any(), any(), any(), any(), any())).thenReturn(Optional.of(saved));
        when(replay.completeResult(any(), any())).thenReturn(1);
    }
    @Test void pendingQueueTransferNeedsNoPhysicalReservationAndRecordsAudit() {
        var result = service.transferRoom(DiagnosticActor.user(doctor, AccountRole.DOCTOR), id, "transfer", 0, newRoom, "Queue room", "queue-test");
        assertEquals(newRoom, result.roomId()); assertEquals(DiagnosticRequestStatus.PENDING, result.status());
        verify(queue).requireEligibleRoom(newRoom, serviceId, "MRI");
        verify(capacity, never()).releaseImagingCapacity(any(), any());
        verify(capacity, never()).reserveImagingCapacity(any(), any(), any(), anyLong());
        verify(audits).save(any());
    }
    @Test void pendingQueueCancellationDoesNotReleaseNonexistentReservation() {
        service.cancel(DiagnosticActor.user(doctor, AccountRole.DOCTOR), id, "cancel", 0,
                CancellationCause.values()[0], "Cancelled", "queue-test");
        verify(capacity, never()).releaseImagingCapacity(any(), any());
        assertEquals(DiagnosticRequestStatus.CANCELLED, row.getStatus());
    }
    @Test void transferStillDeniesWrongDoctorAndReceptionist() {
        assertThrows(DiagnosticOperationException.class, () -> service.transferRoom(DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR), id,
                "wrong", 0, newRoom, "Queue", "queue-test"));
        assertThrows(DiagnosticOperationException.class, () -> service.transferRoom(DiagnosticActor.user(doctor, AccountRole.RECEPTIONIST), id,
                "reception", 0, newRoom, "Queue", "queue-test"));
        verifyNoInteractions(queue, capacity); verify(requests, never()).saveAndFlush(any());
    }
}
