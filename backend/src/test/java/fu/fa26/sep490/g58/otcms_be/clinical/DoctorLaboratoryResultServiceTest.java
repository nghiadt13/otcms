package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderPreparationService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.DoctorLaboratoryResultServiceImpl;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorLaboratoryResultServiceTest {
    @Test
    void blocksAssessmentForUnissuedResultOrDifferentVisit() {
        var care = mock(ClinicalOrderPreparationService.class);
        var orders = mock(ServiceOrderRepository.class);
        var results = mock(LaboratoryDraftService.class);
        var service = new DoctorLaboratoryResultServiceImpl(care, orders, results);
        var visit = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var doctor = UUID.randomUUID();
        var order = new ServiceOrderJpaEntity("LAB-1", UUID.randomUUID(), visit, UUID.randomUUID(), UUID.randomUUID(),
                "CBC", ClinicalServiceType.LABORATORY, doctor, "test", null, null, BigDecimal.ONE, Instant.now());
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(ClinicalOperationException.class,
                () -> service.assess(visit, orderId, doctor, UUID.randomUUID(), "Reviewed"));
        assertThrows(ClinicalOperationException.class,
                () -> service.assess(UUID.randomUUID(), orderId, doctor, UUID.randomUUID(), "Reviewed"));
        verify(results, never()).issued(any());
        verify(orders, never()).recordDoctorResultAssessment(any(), any(), any(), any(), any());
    }

    @Test
    void deniesDoctorWithoutCareAccessBeforeReadingOrdersOrResults() {
        var care = mock(ClinicalOrderPreparationService.class);
        var orders = mock(ServiceOrderRepository.class);
        var results = mock(LaboratoryDraftService.class);
        var visit = UUID.randomUUID();
        var doctor = UUID.randomUUID();
        when(care.requireCurrentCare(visit, doctor)).thenThrow(new ClinicalOperationException("MSG-40"));

        assertThrows(ClinicalOperationException.class,
                () -> new DoctorLaboratoryResultServiceImpl(care, orders, results).list(visit, doctor));
        verifyNoInteractions(orders, results);
    }
}
