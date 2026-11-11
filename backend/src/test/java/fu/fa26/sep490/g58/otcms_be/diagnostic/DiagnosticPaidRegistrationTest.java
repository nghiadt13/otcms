package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.StaffRoomAssignmentQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.staff.integration.diagnostic.StaffDiagnosticRoomStatusAdapter;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.ImagingRoomQueueServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.PageImpl;

/** Real registration -> Room/Staff owner services -> real Technician status queue. Only persistence/Auth/catalog reads are doubled. */
class DiagnosticPaidRegistrationTest {
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DoctorProfileIdentityQueryService identities = mock(DoctorProfileIdentityQueryService.class);
    final DoctorAccountAccessService accounts = mock(DoctorAccountAccessService.class);
    final VisitDiagnosticReadPort visits = mock(VisitDiagnosticReadPort.class);
    final ImagingServiceCatalogPort catalog = mock(ImagingServiceCatalogPort.class);
    final ClinicalImagingOrderPort orders = mock(ClinicalImagingOrderPort.class);
    final VisitRoomJpaRepository roomRows = mock(VisitRoomJpaRepository.class);
    final VisitRoomJpaRepository.ImagingQueueRoomRow room = mock(VisitRoomJpaRepository.ImagingQueueRoomRow.class);
    final StaffRoomAssignmentRepository shifts = mock(StaffRoomAssignmentRepository.class);
    final StaffAccountDirectory directory = mock(StaffAccountDirectory.class);
    final GetClinicServiceCatalogService services = mock(GetClinicServiceCatalogService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneOffset.UTC);
    final StaffRoomAssignmentQueryService staff = new StaffRoomAssignmentQueryServiceImpl(directory, shifts);
    final ImagingRoomQueueServiceImpl rooms = new ImagingRoomQueueServiceImpl(roomRows, staff, services, clock);
    final UUID order = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(),
            account = UUID.randomUUID(), serviceId = UUID.randomUUID(), roomId = UUID.randomUUID(), otherRoom = UUID.randomUUID(),
            technician = UUID.randomUUID(), otherTechnician = UUID.randomUUID(), offShift = UUID.randomUUID();
    final RegisterImagingOrder command = new RegisterImagingOrder(order, visit, patient, doctor, serviceId, roomId, "KNEE", "Pain");
    final DiagnosticImagingRequestRegistrationImpl registration = new DiagnosticImagingRequestRegistrationImpl(requests, identities, accounts, visits, catalog, orders, rooms);
    final Map<UUID, DiagnosticRequestJpaEntity> rows = new LinkedHashMap<>();

    @BeforeEach void setup() {
        when(identities.findDoctorAccountId(doctor)).thenReturn(Optional.of(account));
        when(accounts.isActiveDoctorAccount(account)).thenReturn(true);
        when(visits.requireImagingOrderEligibility(visit, account)).thenReturn(new VisitEvidence(visit, patient, account, true, true, true));
        when(orders.requireOpenImagingOrder(order)).thenReturn(new ClinicalImagingOrder(order, visit, serviceId, true, true));
        when(catalog.requireImagingService(serviceId, null)).thenReturn(new ImagingService(serviceId, "XRAY", true));
        when(services.getService(serviceId)).thenReturn(new ClinicService(serviceId, "X-ray", "", ClinicServiceType.IMAGING,
                java.math.BigDecimal.ONE, Duration.ofMinutes(20), Duration.ofMinutes(5), false, true, "XRAY", null, List.of()));
        when(services.imagingQueueServices(any())).thenReturn(Map.of(serviceId,
                new GetClinicServiceCatalogService.ImagingQueueService(serviceId, ClinicServiceType.IMAGING, "XRAY", true)));
        when(room.getServiceId()).thenReturn(serviceId); when(room.getRoomId()).thenReturn(roomId);
        when(room.getRoomCode()).thenReturn("CDHA-01"); when(room.getDisplayLabel()).thenReturn("Phòng X-quang 1");
        when(room.getActive()).thenReturn(true); when(room.getType()).thenReturn("IMAGING"); when(room.getImagingCapability()).thenReturn(true);
        when(roomRows.findImagingQueueRooms(any())).thenReturn(List.of(room));
        var active = new StaffAccountDirectory.Entry(technician, "tech", "Technician", AccountType.IMAGING_TECHNICIAN);
        when(directory.activeStaff(anyCollection())).thenReturn(List.of(active));
        when(directory.activeStaff(technician)).thenReturn(Optional.of(active));
        when(directory.activeStaff(otherTechnician)).thenReturn(Optional.of(new StaffAccountDirectory.Entry(otherTechnician, "other", "Other", AccountType.IMAGING_TECHNICIAN)));
        when(directory.activeStaff(offShift)).thenReturn(Optional.of(new StaffAccountDirectory.Entry(offShift, "off", "Off", AccountType.IMAGING_TECHNICIAN)));
        when(shifts.findCurrentAssignments(any(), eq(clock.instant()))).thenReturn(List.of(new StaffRoomAssignmentRepository.Assignment(roomId, technician)));
        when(shifts.hasActiveAssignment(technician, roomId, clock.instant())).thenReturn(true);
        when(shifts.hasActiveAssignment(otherTechnician, otherRoom, clock.instant())).thenReturn(true);
        when(requests.findFirstByServiceOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(any(), eq(DiagnosticRequestStatus.CANCELLED)))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))).filter(row -> row.getStatus() != DiagnosticRequestStatus.CANCELLED));
        when(requests.insertAssigned(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(9);
            if (rows.containsKey(id) && rows.get(id).getStatus() != DiagnosticRequestStatus.CANCELLED) return 0;
            var request = new DiagnosticRequestJpaEntity(call.getArgument(0), visit, patient, account, serviceId, call.getArgument(5),
                    "XRAY", "KNEE", "Pain", DiagnosticRequestStatus.PENDING, null, null, null);
            request.setServiceOrderId(id); rows.put(id, request); return 1;
        });
        when(requests.findAllByStatusInOrderByCreatedAtDescIdDesc(any(), any())).thenAnswer(call -> new PageImpl<>(new ArrayList<>(rows.values())));
    }

    @Test void unpaidClinicalOrderCannotRegisterEvenThroughInternalCaller() {
        when(orders.requireOpenImagingOrder(order)).thenReturn(new ClinicalImagingOrder(order, visit, serviceId, true, false));
        assertThrows(DiagnosticOperationException.class, () -> registration.register(command));
        assertTrue(rows.isEmpty()); verifyNoInteractions(roomRows);
    }
    @Test void nullRoomRejectedBeforeAnyInsert() {
        assertThrows(DiagnosticOperationException.class, () -> registration.register(new RegisterImagingOrder(order, visit, patient, doctor, serviceId, null, "KNEE", "Pain")));
        assertTrue(rows.isEmpty()); verifyNoInteractions(roomRows);
    }
    @ParameterizedTest @ValueSource(strings = {"inactive", "type", "unsupported", "capability", "noTechnician", "inactiveAccount", "wrongRole", "missingRoom"})
    void invalidRoomCannotBeOfferedOrDispatched(String failure) {
        switch (failure) {
            case "inactive" -> when(room.getActive()).thenReturn(false);
            case "type" -> when(room.getType()).thenReturn("EXAMINATION");
            case "unsupported" -> when(room.getServiceId()).thenReturn(UUID.randomUUID());
            case "capability" -> when(room.getImagingCapability()).thenReturn(false);
            case "noTechnician" -> when(shifts.findCurrentAssignments(any(), any())).thenReturn(List.of());
            case "inactiveAccount" -> when(directory.activeStaff(anyCollection())).thenReturn(List.of());
            case "wrongRole" -> when(directory.activeStaff(anyCollection())).thenReturn(List.of(new StaffAccountDirectory.Entry(technician, "doctor", "Doctor", AccountType.DOCTOR)));
            case "missingRoom" -> when(roomRows.findImagingQueueRooms(any())).thenReturn(List.of());
        }
        assertTrue(rooms.findEligibleRooms(List.of(serviceId)).getOrDefault(serviceId, List.of()).isEmpty());
        assertEquals(DiagnosticOperationOutcome.ROOM_NOT_READY, assertThrows(DiagnosticOperationException.class, () -> registration.register(command)).outcome());
        assertTrue(rows.isEmpty());
    }
    @Test void inactiveNonImagingOrInvalidCanonicalServicesHaveNoOptions() {
        for (var source : List.of(
                new GetClinicServiceCatalogService.ImagingQueueService(serviceId, ClinicServiceType.IMAGING, "XRAY", false),
                new GetClinicServiceCatalogService.ImagingQueueService(serviceId, ClinicServiceType.EXAMINATION, "XRAY", true),
                new GetClinicServiceCatalogService.ImagingQueueService(serviceId, ClinicServiceType.IMAGING, null, true),
                new GetClinicServiceCatalogService.ImagingQueueService(serviceId, ClinicServiceType.IMAGING, "INVALID", true))) {
            when(services.imagingQueueServices(any())).thenReturn(Map.of(serviceId, source));
            assertTrue(rooms.findEligibleRooms(List.of(serviceId)).isEmpty());
        }
        verifyNoInteractions(roomRows);
    }
    @Test void paidOrderRegistersAssignedPendingExactlyOnceAndDifferentRoomConflicts() {
        var first = registration.register(command); var second = registration.register(command);
        assertEquals(first.requestId(), second.requestId()); assertEquals(1, rows.size());
        assertEquals(roomId, rows.get(order).getRoomId()); assertEquals(DiagnosticRequestStatus.PENDING, rows.get(order).getStatus());
        assertEquals(DiagnosticOperationOutcome.STATE_CONFLICT, assertThrows(DiagnosticOperationException.class,
                () -> registration.register(new RegisterImagingOrder(order, visit, patient, doctor, serviceId, otherRoom, "KNEE", "Pain"))).outcome());
        verify(requests, times(1)).insertAssigned(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
    @Test void occupiedRoomAcceptsMultiplePendingRequestsWithoutCapacityOrReservationDependency() {
        when(roomRows.hasBlockingUse(any(), any())).thenReturn(true);
        assertEquals(roomId, rooms.findEligibleRooms(List.of(serviceId)).get(serviceId).getFirst().roomId());
        registration.register(command);
        UUID nextOrder = UUID.randomUUID();
        when(orders.requireOpenImagingOrder(nextOrder)).thenReturn(new ClinicalImagingOrder(nextOrder, visit, serviceId, true, true));
        registration.register(new RegisterImagingOrder(nextOrder, visit, patient, doctor, serviceId, roomId, "KNEE", "Pain"));
        assertEquals(2, rows.size()); assertTrue(rows.values().stream().allMatch(row -> roomId.equals(row.getRoomId()) && row.getStatus() == DiagnosticRequestStatus.PENDING));
        verify(roomRows, never()).hasBlockingUse(any(), any());
        assertTrue(Arrays.stream(DiagnosticImagingRequestRegistrationImpl.class.getDeclaredFields()).noneMatch(field -> field.getType() == RoomReservationPort.class));
    }
    @Test void paidUndispatchedInvisibleThenOnlyCurrentAssignedRoomTechnicianSeesPendingWork() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("roomStatus", new StaffDiagnosticRoomStatusAdapter(staff, clock));
        var patientIdentities = mock(fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.class);
        when(patientIdentities.findClinicalIdentities(any())).thenReturn(Map.of(patient, new fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.ClinicalIdentity(patient, "Patient", java.time.LocalDate.of(1990, 1, 1), "MALE")));
        var status = new DiagnosticStatusServiceImpl(requests, mock(fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository.class), beans.getBeanProvider(VisitDiagnosticReadPort.class), beans.getBeanProvider(RoomStatusAccessPort.class), patientIdentities, orders);
        var assigned = DiagnosticActor.user(technician, AccountRole.IMAGING_TECHNICIAN);
        var round = new fu.fa26.sep490.g58.otcms_be.clinical.model.entity.OrderingRoundJpaEntity(visit, patient, 1, doctor, clock.instant());
        var clinicalOrder = new fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity("CD-QUEUE", round.getId(), visit, patient,
                serviceId, "X-ray", fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType.IMAGING, doctor,
                "Pain", "KNEE", null, java.math.BigDecimal.ONE, clock.instant());
        var clinicalRounds = mock(fu.fa26.sep490.g58.otcms_be.clinical.repository.OrderingRoundRepository.class);
        var clinicalOrders = mock(fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository.class);
        var guard = mock(fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService.class);
        when(clinicalRounds.findById(round.getId())).thenReturn(Optional.of(round));
        when(clinicalRounds.existsById(round.getId())).thenReturn(true);
        when(clinicalOrders.findByOrderingRoundId(round.getId())).thenReturn(List.of(clinicalOrder));
        when(orders.requireOpenImagingOrder(clinicalOrder.getId())).thenAnswer(call -> new ClinicalImagingOrder(
                clinicalOrder.getId(), visit, serviceId, true,
                clinicalOrder.getPaymentStatus() == fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus.PAID));
        var statuses = mock(fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService.class);
        when(statuses.findByServiceOrders(any())).thenReturn(Map.of());
        var workflow = new fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalRoundWorkflowServiceImpl(clinicalRounds, clinicalOrders,
                mock(fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderPreparationService.class),
                mock(fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.class), statuses, registration, guard,
                rooms, mock(fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort.class));
        var reception = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(UUID.randomUUID(), AccountRole.RECEPTIONIST, UUID.randomUUID());
        var assignments = List.of(new fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalRoundWorkflowService.RoomAssignment(clinicalOrder.getId(), roomId));
        assertThrows(fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException.class,
                () -> workflow.dispatch(round.getId(), reception, assignments));
        new fu.fa26.sep490.g58.otcms_be.clinical.integration.billing.ClinicalRoundBillingAdapter(clinicalRounds, clinicalOrders, guard, clock)
                .markRoundPaid(round.getId());
        assertEquals(fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus.PAID, clinicalOrder.getPaymentStatus());
        assertTrue(rows.isEmpty());
        assertTrue(status.listTechnicianUploadWork(assigned, null, 0, 20).items().isEmpty());
        var dispatched = workflow.dispatch(round.getId(), reception, assignments);
        assertEquals(rows.get(clinicalOrder.getId()).getId(), dispatched.getFirst());
        assertEquals(dispatched, workflow.dispatch(round.getId(), reception, assignments));
        var visible = status.listTechnicianUploadWork(assigned, null, 0, 20).items();
        assertEquals(1, visible.size()); assertEquals(roomId, visible.getFirst().roomId()); assertEquals(DiagnosticRequestStatus.PENDING, visible.getFirst().status());
        assertTrue(status.listTechnicianUploadWork(DiagnosticActor.user(otherTechnician, AccountRole.IMAGING_TECHNICIAN), null, 0, 20).items().isEmpty());
        assertTrue(status.listTechnicianUploadWork(DiagnosticActor.user(offShift, AccountRole.IMAGING_TECHNICIAN), null, 0, 20).items().isEmpty());
        when(shifts.hasActiveAssignment(technician, roomId, clock.instant())).thenReturn(false);
        assertTrue(status.listTechnicianUploadWork(assigned, null, 0, 20).items().isEmpty());
    }
}
