package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration.RegisterImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticImagingRequestRegistrationImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.integration.diagnostic.ImagingServiceCatalogAdapter;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.*;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DoctorImagingRegistrationTest {
    final UUID orderId = UUID.randomUUID(), visitId = UUID.randomUUID(), patientId = UUID.randomUUID(),
            doctorId = UUID.randomUUID(), accountId = UUID.randomUUID(), serviceId = UUID.randomUUID();
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DoctorProfileIdentityQueryService identities = mock(DoctorProfileIdentityQueryService.class);
    final DoctorAccountAccessService accounts = mock(DoctorAccountAccessService.class);
    final VisitDiagnosticReadPort visits = mock(VisitDiagnosticReadPort.class);
    final ImagingServiceCatalogPort catalog = mock(ImagingServiceCatalogPort.class);
    final ClinicalImagingOrderPort orders = mock(ClinicalImagingOrderPort.class);
    final UUID roomId = UUID.randomUUID();
    final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms = mock(fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.class);
    final DiagnosticImagingRequestRegistrationImpl registration = new DiagnosticImagingRequestRegistrationImpl(
            requests, identities, accounts, visits, catalog, orders, rooms);
    RegisterImagingOrder command() { return new RegisterImagingOrder(orderId, visitId, patientId, doctorId, serviceId, roomId, "RIGHT_KNEE", "Pain"); }
    DiagnosticRequestJpaEntity row(DiagnosticRequestStatus status) {
        var row = new DiagnosticRequestJpaEntity(UUID.randomUUID(), visitId, patientId, accountId, serviceId,
                roomId, "MRI", "RIGHT_KNEE", "Pain", status, null, null, null);
        row.setServiceOrderId(orderId); return row;
    }

    @Test void activeSameRoomReplayUsesOriginalRequestIncludingResultsWithoutCatalog() {
        when(identities.findDoctorAccountId(doctorId)).thenReturn(Optional.of(accountId));
        for (var status : List.of(DiagnosticRequestStatus.PENDING, DiagnosticRequestStatus.RESULT_AVAILABLE)) {
            var original = row(status);
            when(requests.findFirstByServiceOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(orderId, DiagnosticRequestStatus.CANCELLED)).thenReturn(Optional.of(original));
            assertEquals(original.getId(), registration.register(command()).requestId());
        }
        verifyNoInteractions(catalog, orders, visits, accounts);
        verify(requests, never()).insertAssigned(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void mismatchedPatientOrWrongProfileCannotReuseExistingRequest() {
        var original = row(DiagnosticRequestStatus.PENDING);
        when(requests.findFirstByServiceOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(orderId, DiagnosticRequestStatus.CANCELLED)).thenReturn(Optional.of(original));
        assertThrows(DiagnosticOperationException.class, () -> registration.register(new RegisterImagingOrder(
                orderId, visitId, UUID.randomUUID(), doctorId, serviceId, roomId, "RIGHT_KNEE", "Pain")));
        when(identities.findDoctorAccountId(doctorId)).thenReturn(Optional.of(UUID.randomUUID()));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, assertThrows(DiagnosticOperationException.class,
                () -> registration.register(command())).outcome());
        verifyNoInteractions(catalog, orders, visits);
    }

    @Test void missingDoctorOrInactiveAccountPreventsAnyInsert() {
        assertThrows(DiagnosticOperationException.class, () -> registration.register(command()));
        when(identities.findDoctorAccountId(doctorId)).thenReturn(Optional.of(accountId));
        assertThrows(DiagnosticOperationException.class, () -> registration.register(command()));
        verifyNoInteractions(visits, catalog, orders);
        verify(requests, never()).insertAssigned(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void eligibilityAndLinkageFailuresPreventInsertion() {
        when(identities.findDoctorAccountId(doctorId)).thenReturn(Optional.of(accountId));
        when(accounts.isActiveDoctorAccount(accountId)).thenReturn(true);
        when(visits.requireImagingOrderEligibility(visitId, accountId)).thenReturn(new VisitEvidence(visitId, patientId, accountId, false, true, false));
        assertEquals(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE, assertThrows(DiagnosticOperationException.class,
                () -> registration.register(command())).outcome());
        when(visits.requireImagingOrderEligibility(visitId, accountId)).thenReturn(new VisitEvidence(visitId, patientId, accountId, true, true, true));
        when(orders.requireOpenImagingOrder(orderId)).thenReturn(null);
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, assertThrows(DiagnosticOperationException.class,
                () -> registration.register(command())).outcome());
        verifyNoInteractions(catalog); verify(requests, never()).insertAssigned(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void catalogueUsesExplicitStoredModalityAndRejectsOtherTypesMissingInactiveAndUnsetModality() {
        var source = mock(GetClinicServiceCatalogService.class);
        var adapter = new ImagingServiceCatalogAdapter(source);
        for (var type : ClinicServiceType.values()) {
            var service = service(type, true, "MRI"); when(source.getService(serviceId)).thenReturn(service);
            if (type == ClinicServiceType.IMAGING) assertEquals("MRI", adapter.requireImagingService(serviceId, null).imagingType());
            else assertThrows(DiagnosticOperationException.class, () -> adapter.requireImagingService(serviceId, null));
        }
        when(source.getService(serviceId)).thenReturn(service(ClinicServiceType.IMAGING, false, "MRI"));
        assertThrows(DiagnosticOperationException.class, () -> adapter.requireImagingService(serviceId, null));
        when(source.getService(serviceId)).thenReturn(service(ClinicServiceType.IMAGING, true, null));
        assertThrows(DiagnosticOperationException.class, () -> adapter.requireImagingService(serviceId, null));
        when(source.getService(serviceId)).thenReturn(null);
        assertThrows(DiagnosticOperationException.class, () -> adapter.requireImagingService(serviceId, null));
        when(source.getService(serviceId)).thenReturn(service(ClinicServiceType.IMAGING, true, "MRI"));
        assertThrows(DiagnosticOperationException.class, () -> adapter.requireImagingService(serviceId, "CT"));
    }

    private ClinicService service(ClinicServiceType type, boolean active, String modality) {
        return new ClinicService(serviceId, "Imaging", "", type, BigDecimal.ONE, Duration.ofMinutes(30), Duration.ZERO,
                false, active, modality, null, List.of());
    }
}
