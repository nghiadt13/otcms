package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderPreparationService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderingTransactionService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalLaboratoryRequestServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalLaboratoryRequestServiceTest {
    private final UUID visit = UUID.randomUUID(), doctor = UUID.randomUUID(), patient = UUID.randomUUID(), serviceId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-17T00:00:00Z");
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicServiceCatalogPort catalog = mock(ClinicServiceCatalogPort.class);
    private final ClinicalOrderingTransactionService writer = mock(ClinicalOrderingTransactionService.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final ClinicalOrderingRoundRepository rounds = mock(ClinicalOrderingRoundRepository.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final VisitConclusionPort conclusions = mock(VisitConclusionPort.class);
    private final ClinicalLaboratoryRequestServiceImpl service = new ClinicalLaboratoryRequestServiceImpl(
            new ClinicalOrderPreparationService(visits, catalog), writer, orders, rounds, guard, conclusions,
            Clock.fixed(now, ZoneOffset.UTC), idempotency);

    @Test void createsLabRoundOnlyForResponsibleDoctorWithConsent() {
        when(idempotency.find(any(), eq(doctor), eq("key"))).thenReturn(Optional.empty());
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("120000.00"), true));
        var round = new ClinicalOrderingService.RoundView(UUID.randomUUID(), visit, 1, doctor, now, List.of());
        when(writer.createRound(eq(visit), eq(patient), eq(doctor), anyList(), eq("key"), anyString())).thenReturn(round);
        var result = service.submit(request());
        assertFalse(result.replayed());
        assertEquals(round.id(), result.round().id());
        verify(guard).acquire();
        verify(conclusions).markAwaitingConclusion(visit, now);
    }

    @Test void rejectsAnotherDoctorsVisitBeforeWritingOrBilling() {
        when(idempotency.find(any(), eq(doctor), eq("key"))).thenReturn(Optional.empty());
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, UUID.randomUUID(), true));
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> service.submit(request())).code());
        verifyNoInteractions(writer, conclusions, catalog);
    }

    @Test void rejectsImagingServices() {
        when(idempotency.find(any(), eq(doctor), eq("key"))).thenReturn(Optional.empty());
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, true));
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "Imaging", ClinicalServiceType.IMAGING, BigDecimal.TEN, true));
        assertThrows(ClinicalOperationException.class, () -> service.submit(request()));
        verifyNoInteractions(writer, conclusions);
    }

    @Test void refusesMissingConsent() {
        var command = new SubmitRequest(visit, doctor, "key", false, List.of(new OrderRequest(serviceId, "Reason", null)));
        assertThrows(ClinicalOperationException.class, () -> service.submit(command));
        verifyNoInteractions(orders, writer, conclusions, catalog, visits, guard);
    }

    private SubmitRequest request() {
        return new SubmitRequest(visit, doctor, "key", true, List.of(new OrderRequest(serviceId, "Reason", null)));
    }
}
