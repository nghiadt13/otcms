package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.ImagingExecutionTestFixtures.Row;
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
    final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    final AuditLogWriter audits = mock(AuditLogWriter.class);
    final ClinicalImagingOrderPort orders = mock(ClinicalImagingOrderPort.class);
    final RoomReservationPort capacity = mock(RoomReservationPort.class);
    final ImagingRoomQueueService queue = mock(ImagingRoomQueueService.class);
    final VisitDiagnosticReadPort visits = mock(VisitDiagnosticReadPort.class);
    final Row row = new Row(id, visit, patient, doctor, serviceId, oldRoom,
            "MRI", "KNEE", "Pain", DiagnosticRequestStatus.PENDING, null, null, null);
    DiagnosticRequestServiceImpl service;
    @BeforeEach void setup() {
        row.setCreatedAt(java.time.Instant.EPOCH);
        var beans = new StaticListableBeanFactory(); beans.addBean("capacity", capacity); beans.addBean("queue", queue); beans.addBean("visit", visits); beans.addBean("orders", orders);
        service = new DiagnosticRequestServiceImpl(requests, audits,
                new DiagnosticAuthorizationPolicyImpl(new DiagnosticTransitionPolicyImpl()), beans.getBeanProvider(VisitDiagnosticReadPort.class),
                beans.getBeanProvider(RoomReservationPort.class), beans.getBeanProvider(ImagingServiceCatalogPort.class),
                beans.getBeanProvider(ClinicalImagingOrderPort.class), beans.getBeanProvider(ImagingRoomQueueService.class));
        when(requests.find(id)).thenAnswer(ignored -> Optional.of(ImagingExecutionTestFixtures.execution(row)));
        when(requests.findForUpdate(id)).thenAnswer(ignored -> Optional.of(ImagingExecutionTestFixtures.execution(row)));
        when(requests.transferRoom(eq(id), anyLong(), any(), any(), any())).thenAnswer(call -> { row.setRoomId(call.getArgument(2)); return 1; });
        when(requests.cancel(eq(id), anyLong(), any(), any(), any(), any())).thenAnswer(call -> { row.setStatus(DiagnosticRequestStatus.CANCELLED); return 1; });
        when(visits.requireImagingOrderEligibility(eq(visit), any())).thenReturn(new VisitEvidence(visit, patient, doctor, true, true, true));
    }
    @Test void pendingQueueTransferNeedsNoPhysicalReservationAndRecordsAudit() {
        var result = service.transferRoom(DiagnosticActor.user(doctor, AccountRole.DOCTOR), id, "transfer", 0, newRoom, "Queue room", "queue-test");
        assertEquals(newRoom, result.roomId()); assertEquals(DiagnosticRequestStatus.PENDING, result.status());
        verify(queue).requireEligibleRoom(newRoom, serviceId, "MRI");
        verify(capacity, never()).releaseImagingCapacity(any(), any());
        verify(capacity, never()).reserveImagingCapacity(any(), any(), any(), anyLong());
        verify(audits).record(any());
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
        verifyNoInteractions(queue, capacity); verify(requests, never()).transferRoom(any(), anyLong(), any(), any(), any());
    }
}
