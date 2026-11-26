package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalLaboratoryDispatchServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalLaboratoryDispatchServiceTest {
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID();
    final UUID room = UUID.randomUUID(), serviceId = UUID.randomUUID(), account = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-17T00:00:00Z");
    final ServiceOrderJpaEntity order = new ServiceOrderJpaEntity("LAB-1", UUID.randomUUID(), visit, patient, serviceId,
            "CBC", ClinicalServiceType.LABORATORY, doctor, "Reason", null, null, BigDecimal.TEN, now);
    final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    final LaboratoryRoomQueryService rooms = mock(LaboratoryRoomQueryService.class);
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final AuditLogWriter audit = mock(AuditLogWriter.class);
    final ClinicalLaboratoryDispatchServiceImpl service = new ClinicalLaboratoryDispatchServiceImpl(orders, visits, rooms,
            guard, audit, Clock.fixed(now, ZoneOffset.UTC));
    final AuthenticatedAccount receptionist = new AuthenticatedAccount(account, AccountRole.RECEPTIONIST, UUID.randomUUID());

    private void unassigned() {
        var assignment = mock(ServiceOrderRepository.LaboratoryAssignmentRow.class);
        when(orders.findLaboratoryAssignment(order.getId())).thenReturn(Optional.of(assignment));
    }

    @Test void unpaidOrderIsRejectedBeforeRoomAssignment() {
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertEquals("MSG-95", assertThrows(ClinicalOperationException.class,
                () -> service.assign(order.getId(), room, receptionist)).code());
        verifyNoInteractions(rooms, visits, audit);
    }

    @Test void receptionistMayFindEligibleRoomsBeforePayment() {
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        var available = new LaboratoryRoomQueryService.Room(room, "LAB-1", "Phòng xét nghiệm");
        when(rooms.eligibleRooms(serviceId)).thenReturn(java.util.List.of(available));
        assertEquals(java.util.List.of(available), service.eligibleRooms(order.getId(), receptionist));
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.eligibleRooms(order.getId(), new AuthenticatedAccount(account, AccountRole.DOCTOR, UUID.randomUUID()))).code());
    }

    @Test void paidOrderIsAssignedOnlyToEligibleRoomAndAudited() {
        order.markPaid(now);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        unassigned();
        when(rooms.isEligibleRoom(room, serviceId)).thenReturn(true);
        when(orders.assignLaboratoryRoom(order.getId(), room, now)).thenReturn(1);
        service.assign(order.getId(), room, receptionist);
        verify(audit).record(argThat(entry -> entry.entityId().equals(order.getId()) && entry.actorAccountId().equals(account)));
    }

    @Test void wrongRoleAndIneligibleRoomAreRejected() {
        var doctorAccount = new AuthenticatedAccount(account, AccountRole.DOCTOR, UUID.randomUUID());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.assign(order.getId(), room, doctorAccount)).code());
        order.markPaid(now);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        unassigned();
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.assign(order.getId(), room, receptionist)).code());
        verify(orders, never()).assignLaboratoryRoom(any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test void exactRetryReturnsWithoutRepeatingAuditButDifferentRoomIsRejected() {
        order.markPaid(now);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        var assignment = mock(ServiceOrderRepository.LaboratoryAssignmentRow.class);
        when(assignment.getLabStatus()).thenReturn("ASSIGNED");
        when(assignment.getRoomId()).thenReturn(room);
        when(orders.findLaboratoryAssignment(order.getId())).thenReturn(Optional.of(assignment));
        service.assign(order.getId(), room, receptionist);
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.assign(order.getId(), UUID.randomUUID(), receptionist)).code());
        verify(orders, never()).assignLaboratoryRoom(any(), any(), any());
        verifyNoInteractions(audit, rooms);
    }
}
