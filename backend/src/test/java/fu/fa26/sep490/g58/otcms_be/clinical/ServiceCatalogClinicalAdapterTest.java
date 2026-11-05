package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.servicecatalog.ServiceCatalogClinicalAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceCatalogClinicalAdapterTest {
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final ServiceCatalogClinicalAdapter adapter = new ServiceCatalogClinicalAdapter(catalog);
    private final UUID id = UUID.randomUUID();

    @Test
    void imagingMapsPublicCatalogIdentityNamePriceAndActiveFlag() {
        when(catalog.getService(id)).thenReturn(service(ClinicServiceType.IMAGING, true));
        var snapshot = adapter.getService(id);
        assertEquals(id, snapshot.serviceId());
        assertEquals("Service", snapshot.name());
        assertEquals(ClinicalServiceType.IMAGING, snapshot.type());
        assertEquals(new BigDecimal("150.50"), snapshot.currentPrice());
        assertTrue(snapshot.active());
        verify(catalog).getService(id);
    }

    @Test
    void laboratoryMapsAndPreservesInactiveFlagForOrderingValidation() {
        when(catalog.getService(id)).thenReturn(service(ClinicServiceType.LABORATORY, false));
        var snapshot = adapter.getService(id);
        assertEquals(ClinicalServiceType.LABORATORY, snapshot.type());
        assertFalse(snapshot.active());
    }

    @Test
    void examinationIsRejectedRatherThanMappedToOther() {
        when(catalog.getService(id)).thenReturn(service(ClinicServiceType.EXAMINATION, true));
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class, () -> adapter.getService(id)).code());
    }

    @Test
    void missingServiceTranslatesOnlyKnownCatalogNotFound() {
        when(catalog.getService(id)).thenThrow(new ClinicServiceException("SERVICE_NOT_FOUND"));
        assertEquals("MSG-41", assertThrows(ClinicalOperationException.class, () -> adapter.getService(id)).code());
    }

    @Test
    void unexpectedCatalogFailureIsNotHiddenAsNotFound() {
        var failure = new ClinicServiceException("CATALOG_FAILURE");
        when(catalog.getService(id)).thenThrow(failure);
        assertSame(failure, assertThrows(ClinicServiceException.class, () -> adapter.getService(id)));
    }

    @Test
    void nullIdDoesNotCallCatalog() {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class, () -> adapter.getService(null)).code());
        verifyNoInteractions(catalog);
    }

    @Test
    void unavailableAndUntypedCatalogResponsesFailClosed() {
        when(catalog.getService(id)).thenReturn(null);
        assertEquals("MSG-41", assertThrows(ClinicalOperationException.class, () -> adapter.getService(id)).code());
        when(catalog.getService(id)).thenReturn(service(null, true));
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class, () -> adapter.getService(id)).code());
    }

    @Test
    void mismatchedCatalogIdentityIsRejected() {
        var otherId = UUID.randomUUID();
        when(catalog.getService(otherId)).thenReturn(service(ClinicServiceType.IMAGING, true));
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class, () -> adapter.getService(otherId)).code());
    }

    private ClinicService service(ClinicServiceType type, boolean active) {
        return new ClinicService(id, "Service", null, type, new BigDecimal("150.50"),
                Duration.ofMinutes(20), Duration.ZERO, false, active);
    }
}
