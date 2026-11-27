package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository.ImagingQueueServiceRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class GetClinicServiceCatalogServiceImplTest {
    private final ClinicServiceRepository services = mock(ClinicServiceRepository.class);
    private final GetClinicServiceCatalogServiceImpl query = new GetClinicServiceCatalogServiceImpl(services);

    private static ClinicServiceJpaEntity entity(UUID id, String name, ClinicServiceType type,
            boolean onlineBookable, boolean active, String modality) {
        return ClinicServiceMapper.toEntity(new ClinicService(id, name, "Desc", type,
                new BigDecimal("100000.00"), Duration.ofMinutes(30), Duration.ofMinutes(5),
                onlineBookable, active, modality, null, List.of()));
    }

    @Test
    void imagingQueueLookupWithNoIdsReturnsEmptyMapWithoutQuerying() {
        assertTrue(query.imagingQueueServices(List.of()).isEmpty());
        verifyNoInteractions(services);
    }

    @Test
    void imagingQueueLookupWithNullReturnsEmptyMapWithoutQuerying() {
        assertTrue(query.imagingQueueServices(null).isEmpty());
        verifyNoInteractions(services);
    }

    @Test
    void imagingQueueLookupReturnsMappedQueueServicesForNonEmptyIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ImagingQueueServiceRow row1 = mock(ImagingQueueServiceRow.class);
        when(row1.getId()).thenReturn(id1);
        when(row1.getType()).thenReturn(ClinicServiceType.IMAGING);
        when(row1.getModality()).thenReturn("XRAY");
        when(row1.getActive()).thenReturn(true);

        ImagingQueueServiceRow row2 = mock(ImagingQueueServiceRow.class);
        when(row2.getId()).thenReturn(id2);
        when(row2.getType()).thenReturn(ClinicServiceType.IMAGING);
        when(row2.getModality()).thenReturn("MRI");
        when(row2.getActive()).thenReturn(false);

        var ids = List.of(id1, id2);
        when(services.findImagingQueueServices(ids)).thenReturn(List.of(row1, row2));

        var result = query.imagingQueueServices(ids);

        assertEquals(2, result.size());
        assertEquals("XRAY", result.get(id1).modality());
        assertTrue(result.get(id1).active());
        assertEquals("MRI", result.get(id2).modality());
    }

    @Test
    void getServiceRejectsMissingOrUnknownIdAsNotFound() {
        assertEquals("SERVICE_NOT_FOUND",
                assertThrows(ClinicServiceException.class, () -> query.getService(null)).getMessage());
        UUID unknown = UUID.randomUUID();
        when(services.findById(unknown)).thenReturn(Optional.empty());
        assertEquals("SERVICE_NOT_FOUND",
                assertThrows(ClinicServiceException.class, () -> query.getService(unknown)).getMessage());
    }

    @Test
    void getServiceReturnsMappedServiceForValidId() {
        UUID id = UUID.randomUUID();
        when(services.findById(id)).thenReturn(Optional.of(
                entity(id, "X-ray knee", ClinicServiceType.IMAGING, true, true, "XRAY")));

        ClinicService result = query.getService(id);

        assertEquals(id, result.id());
        assertEquals("X-ray knee", result.name());
        assertEquals(ClinicServiceType.IMAGING, result.type());
        assertEquals("XRAY", result.modality());
    }

    @Test
    void adminCatalogReadsEveryServiceSortedByNameAndId() {
        when(services.findAll(any(Sort.class))).thenReturn(List.of());

        assertEquals(List.of(), query.adminCatalog());
        verify(services).findAll(Sort.by("name", "id"));
    }

    @Test
    void publicCatalogReturnsActiveOnlineBookableServices() {
        UUID id = UUID.randomUUID();
        when(services.findByActiveTrueAndOnlineBookableTrueOrderByNameAscIdAsc()).thenReturn(List.of(
                entity(id, "Consult", ClinicServiceType.EXAMINATION, true, true, null)));

        var result = query.publicCatalog();

        assertEquals(1, result.size());
        assertEquals(id, result.getFirst().id());
        verify(services).findByActiveTrueAndOnlineBookableTrueOrderByNameAscIdAsc();
    }

    @Test
    void staffCatalogReturnsActiveServices() {
        UUID id = UUID.randomUUID();
        when(services.findByActiveTrueOrderByNameAscIdAsc()).thenReturn(List.of(
                entity(id, "Internal consult", ClinicServiceType.EXAMINATION, false, true, null)));

        var result = query.staffCatalog();

        assertEquals(1, result.size());
        assertEquals("Internal consult", result.getFirst().name());
        verify(services).findByActiveTrueOrderByNameAscIdAsc();
    }

    @Test
    void roomOptionsWithInactiveIncludedReadsEveryServiceRegardlessOfStatus() {
        when(services.findAll()).thenReturn(List.of());

        assertEquals(List.of(), query.roomOptions(null, true));
        verify(services).findAll();
        verify(services, never()).findByActiveTrueOrderByNameAscIdAsc();
    }

    @Test
    void roomOptionsWithoutInactiveReadsOnlyActiveServices() {
        when(services.findByActiveTrueOrderByNameAscIdAsc()).thenReturn(List.of());

        assertEquals(List.of(), query.roomOptions(null, false));
        verify(services).findByActiveTrueOrderByNameAscIdAsc();
        verify(services, never()).findAll();
    }

    @Test
    void roomOptionsFiltersByNonNullTypeAndSortsCaseInsensitively() {
        UUID idB = UUID.randomUUID();
        UUID idA = UUID.randomUUID();
        UUID idExam = UUID.randomUUID();
        when(services.findAll()).thenReturn(List.of(
                entity(idB, "ultrasound", ClinicServiceType.IMAGING, true, false, "ULTRASOUND"),
                entity(idExam, "Consult", ClinicServiceType.EXAMINATION, true, true, null),
                entity(idA, "CT Scan", ClinicServiceType.IMAGING, true, true, "CT")));

        var result = query.roomOptions(ClinicServiceType.IMAGING, true);

        assertEquals(List.of("CT Scan", "ultrasound"), result.stream().map(ClinicService::name).toList());
    }
}
