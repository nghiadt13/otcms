package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.billing.ClinicalRoundBillingAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;

class ClinicalRoundWorkflowTest {
    final OrderingRoundRepository rounds = mock(OrderingRoundRepository.class);
    final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    final ClinicalOrderPreparationService preparation = mock(ClinicalOrderPreparationService.class);
    final PatientClinicalIdentityQueryService patients = mock(PatientClinicalIdentityQueryService.class);
    final DiagnosticOrderStatusQueryService statuses = mock(DiagnosticOrderStatusQueryService.class);
    final DiagnosticImagingRequestRegistration registration = mock(DiagnosticImagingRequestRegistration.class);
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), serviceId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-06T12:00:00Z");
    final OrderingRoundJpaEntity round = new OrderingRoundJpaEntity(visit, patient, 2, doctor, now);
    final ServiceOrderJpaEntity order = new ServiceOrderJpaEntity("CD-1", round.getId(), visit, patient, serviceId,
            "Snapshot X-ray", ClinicalServiceType.IMAGING, doctor, "Pain", "KNEE", null, new BigDecimal("150000.00"), now);
    final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService roomQueries = mock(fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.class);
    final VisitClinicalContextPort contexts = mock(VisitClinicalContextPort.class);
    final UUID roomId = UUID.randomUUID();
    List<ClinicalRoundWorkflowService.RoomAssignment> assignments() { return List.of(new ClinicalRoundWorkflowService.RoomAssignment(order.getId(), roomId)); }
    final ClinicalRoundWorkflowService workflow = new ClinicalRoundWorkflowServiceImpl(rounds, orders, preparation, patients, statuses, registration, guard, roomQueries, contexts);
    final ClinicalRoundBillingAdapter billing = new ClinicalRoundBillingAdapter(rounds, orders, guard, Clock.fixed(now, ZoneOffset.UTC));
    AuthenticatedAccount actor(AccountRole role) { return new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID()); }
    @BeforeEach void setup() {
        when(rounds.findById(round.getId())).thenReturn(Optional.of(round));
        when(rounds.existsById(round.getId())).thenReturn(true);
        when(orders.findByOrderingRoundId(round.getId())).thenReturn(List.of(order));
        when(statuses.findByServiceOrders(any())).thenReturn(Map.of());
    }
    @Test void unpaidDispatchFailsBeforeRegistration() {
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class, () -> workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), assignments())).code());
        verifyNoInteractions(registration);
    }
    @Test void onlyReceptionistMayDispatchOrReadQueue() {
        for (var role : AccountRole.values()) if (role != AccountRole.RECEPTIONIST) {
            assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> workflow.dispatch(round.getId(), actor(role), assignments())).code());
            assertThrows(ClinicalOperationException.class, () -> workflow.listReceptionRounds(actor(role), 0, 20));
        }
        verifyNoInteractions(registration, patients);
    }
    @Test void paidDispatchUsesOnlyAuthoritativeFieldsAndRetryReturnsSameRequest() {
        UUID requestId = UUID.randomUUID();
        when(registration.register(any())).thenReturn(new DiagnosticImagingRequestRegistration.RegistrationResult(requestId));
        billing.markRoundPaid(round.getId());
        var expected = new DiagnosticImagingRequestRegistration.RegisterImagingOrder(order.getId(), visit, patient, doctor, serviceId, roomId, "KNEE", "Pain");
        assertEquals(List.of(requestId), workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), assignments()));
        assertEquals(List.of(requestId), workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), assignments()));
        verify(registration, times(2)).register(expected);
    }
    @Test void cancelledOrdersAreExcludedFromBillingAndDispatchAndCannotBecomePaid() {
        order.cancel("Declined", now);
        billing.markRoundPaid(round.getId());
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
        assertThrows(fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException.class, () -> billing.requirePayableRound(round.getId()));
        assertThrows(ClinicalOperationException.class, () -> workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), assignments()));
        verifyNoInteractions(registration);
    }
    @Test void paymentIsIdempotentAndUsesSnapshotWithoutCatalog() {
        assertEquals(new BigDecimal("150000.00"), billing.requirePayableRound(round.getId()).orders().getFirst().priceSnapshot());
        billing.markRoundPaid(round.getId());
        Instant updated = order.getUpdatedAt(); order.markPaid(now.plusSeconds(100));
        assertEquals(updated, order.getUpdatedAt()); assertEquals(ServiceOrderPaymentStatus.PAID, order.getPaymentStatus());
    }
    @Test void doctorReadPersistsUnpaidOrdersWithoutDiagnosticAndRequiresCurrentCare() {
        when(rounds.findByVisitId(visit)).thenReturn(List.of(round)); when(orders.findByVisitId(visit)).thenReturn(List.of(order));
        var result = workflow.listDoctorOrders(visit, doctor);
        assertEquals(1, result.size()); assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, result.getFirst().paymentStatus());
        assertNull(result.getFirst().diagnostic()); assertEquals(2, result.getFirst().roundNumber());
        verify(preparation).requireCurrentCare(visit, doctor);
    }
    @Test void receptionQueueBatchesPatientIdentityAndOrderStatus() {
        when(rounds.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(round)));
        when(orders.findByOrderingRoundIdIn(List.of(round.getId()))).thenReturn(List.of(order));
        when(patients.findClinicalIdentities(List.of(patient))).thenReturn(Map.of(patient,
                new PatientClinicalIdentityQueryService.ClinicalIdentity(patient, "Patient A", LocalDate.of(1990, 1, 1), "OTHER")));
        var result = workflow.listReceptionRounds(actor(AccountRole.RECEPTIONIST), 0, 20);
        assertEquals("Patient A", result.items().getFirst().patientName());
        assertEquals(order.getId(), result.items().getFirst().orders().getFirst().orderId());
        verify(patients, times(1)).findClinicalIdentities(List.of(patient));
        verify(statuses, times(1)).findByServiceOrders(List.of(order.getId()));
    }
    @Test void assignmentsArePerOrderAndAllPersistedFieldsRemainAuthoritative() {
        var second = new ServiceOrderJpaEntity("CD-2", round.getId(), visit, patient, UUID.randomUUID(), "MRI",
                ClinicalServiceType.IMAGING, doctor, "Reason 2", "HIP", null, BigDecimal.TEN, now);
        order.markPaid(now); second.markPaid(now);
        when(orders.findByOrderingRoundId(round.getId())).thenReturn(List.of(order, second));
        when(registration.register(any())).thenAnswer(call -> new DiagnosticImagingRequestRegistration.RegistrationResult(
                ((DiagnosticImagingRequestRegistration.RegisterImagingOrder) call.getArgument(0)).serviceOrderId()));
        UUID secondRoom = UUID.randomUUID();
        var result = workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), List.of(
                new ClinicalRoundWorkflowService.RoomAssignment(order.getId(), roomId),
                new ClinicalRoundWorkflowService.RoomAssignment(second.getId(), secondRoom)));
        assertEquals(List.of(order.getId(), second.getId()), result);
        verify(registration).register(new DiagnosticImagingRequestRegistration.RegisterImagingOrder(second.getId(), visit,
                patient, doctor, second.getServiceId(), secondRoom, "HIP", "Reason 2"));
    }
    @Test void duplicateMissingNullAndForeignAssignmentsFailBeforeAnyRegistration() {
        order.markPaid(now);
        for (var invalid : List.of(List.<ClinicalRoundWorkflowService.RoomAssignment>of(),
                List.of(new ClinicalRoundWorkflowService.RoomAssignment(order.getId(), null)),
                List.of(new ClinicalRoundWorkflowService.RoomAssignment(UUID.randomUUID(), roomId)),
                List.of(assignments().getFirst(), assignments().getFirst()))) {
            assertThrows(ClinicalOperationException.class, () -> workflow.dispatch(round.getId(), actor(AccountRole.RECEPTIONIST), invalid));
        }
        verifyNoInteractions(registration);
    }
    @Test void roomOptionsAreBatchedAndVisibleBeforePaymentButRequireCurrentClinicalLinkage() {
        when(orders.findAllById(List.of(order.getId()))).thenReturn(List.of(order));
        when(contexts.getVisitContexts(List.of(visit))).thenReturn(Map.of(visit,
                new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true)));
        var room = new fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.QueueRoom(roomId, "CDHA-01", "X-ray");
        when(roomQueries.findEligibleRooms(List.of(serviceId))).thenReturn(Map.of(serviceId, List.of(room)));
        var result = workflow.eligibleImagingRooms(List.of(order.getId()), actor(AccountRole.RECEPTIONIST));
        assertEquals(List.of(room), result.getFirst().items());
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
        verify(contexts).getVisitContexts(List.of(visit)); verify(roomQueries).findEligibleRooms(List.of(serviceId));
        when(contexts.getVisitContexts(any())).thenReturn(Map.of(visit,
                new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, false)));
        assertThrows(ClinicalOperationException.class, () -> workflow.eligibleImagingRooms(List.of(order.getId()), actor(AccountRole.RECEPTIONIST)));
    }

}
