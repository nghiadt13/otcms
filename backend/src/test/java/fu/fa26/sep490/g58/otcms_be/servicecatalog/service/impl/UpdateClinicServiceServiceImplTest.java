package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateClinicServiceServiceImplTest {

    private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock private ClinicServiceRepository services;
    @Mock private CoordinationGuardService guard;

    private UpdateClinicServiceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UpdateClinicServiceServiceImpl(services, guard);
    }

    private static ClinicServiceJpaEntity existingEntity(ClinicServiceType type, boolean active) {
        return ClinicServiceMapper.toEntity(new ClinicService(
                SERVICE_ID, "Kham co xuong khop", "Mo ta cu", type, new BigDecimal("200000"),
                Duration.ofMinutes(30), Duration.ofMinutes(10), true, active,
                type == ClinicServiceType.IMAGING ? "XRAY" : null, "Note", List.of()));
    }

    @Test
    void updateExaminationServiceNormalizesNameAndPreservesSchedule() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.EXAMINATION, true)));
        when(services.existsByNameIgnoreCaseAndActiveTrueAndIdNot("Kham khop goi", SERVICE_ID)).thenReturn(false);
        when(services.save(any())).thenAnswer(call -> call.getArgument(0));

        var updated = service.update(SERVICE_ID, "  Kham   khop goi  ", "  Mo ta moi ",
                new BigDecimal("250000"), 45, 15, false);

        assertEquals("Kham khop goi", updated.name());
        assertEquals("Mo ta moi", updated.description());
        assertEquals(Duration.ofMinutes(45), updated.consultationDuration());
        assertEquals(Duration.ofMinutes(15), updated.bufferDuration());
        assertFalse(updated.onlineBookable());
        verify(guard).acquire();
    }

    @Test
    void updateImagingServicePreservesExistingDurationAndUpdatesModality() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.IMAGING, true)));
        when(services.existsByNameIgnoreCaseAndActiveTrueAndIdNot("Chup MRI goi", SERVICE_ID)).thenReturn(false);
        when(services.save(any())).thenAnswer(call -> call.getArgument(0));

        var updated = service.update(SERVICE_ID, "Chup MRI goi", "Mo ta MRI",
                new BigDecimal("1500000"), 60, 20, "MRI", "Nhin an", List.of());

        assertEquals("MRI", updated.modality());
        assertEquals("Nhin an", updated.patientNote());
        assertEquals(Duration.ofMinutes(30), updated.consultationDuration());
        assertEquals(Duration.ofMinutes(10), updated.bufferDuration());
    }

    @Test
    void updateLabServiceWithNestedIndicatorsSavesUpdatedCatalogDetails() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.LABORATORY, true)));
        when(services.existsByNameIgnoreCaseAndActiveTrueAndIdNot("Xet nghiem mau", SERVICE_ID)).thenReturn(false);
        when(services.save(any())).thenAnswer(call -> call.getArgument(0));
        var indicator = new LabIndicator("WBC", "10^9/L",
                new LabIndicator.ReferenceRange(4.0, 10.0), new LabIndicator.ReferenceRange(4.0, 10.0));

        var updated = service.update(SERVICE_ID, "Xet nghiem mau", "Mo ta",
                new BigDecimal("120000"), 30, 10, true, null, "Lay mau sang", List.of(indicator));

        assertEquals(1, updated.indicators().size());
        assertEquals("WBC", updated.indicators().get(0).name());
        assertEquals("Lay mau sang", updated.patientNote());
    }

    @Test
    void updateRejectsNullIdOrInvalidPriceBeforeAcquiringGuard() {
        assertThrows(ClinicServiceException.class,
                () -> service.update(null, "Kham", "Mo ta", new BigDecimal("100000"), 30, 10, true));
        assertThrows(ClinicServiceException.class,
                () -> service.update(SERVICE_ID, "Kham", "Mo ta", new BigDecimal("-1"), 30, 10, true));
        verifyNoInteractions(guard, services);
    }

    @Test
    void updateRejectsInactiveOrMissingServiceAfterAcquiringGuard() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.EXAMINATION, false)));

        ClinicServiceException ex = assertThrows(ClinicServiceException.class,
                () -> service.update(SERVICE_ID, "Kham", "Mo ta", new BigDecimal("100000"), 30, 10, true));

        assertEquals("SERVICE_NOT_FOUND", ex.getMessage());
        verify(guard).acquire();
        verify(services, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateActiveServiceNameWithoutSaving() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.EXAMINATION, true)));
        when(services.existsByNameIgnoreCaseAndActiveTrueAndIdNot("Kham trung", SERVICE_ID)).thenReturn(true);

        ClinicServiceException ex = assertThrows(ClinicServiceException.class,
                () -> service.update(SERVICE_ID, "Kham trung", "Mo ta", new BigDecimal("100000"), 30, 10, true));

        assertEquals("DUPLICATE_SERVICE_NAME", ex.getMessage());
        verify(services, never()).save(any());
    }

    @Test
    void updateAcceptsZeroPriceAndZeroBufferAtLowerBoundary() {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(existingEntity(ClinicServiceType.EXAMINATION, true)));
        when(services.existsByNameIgnoreCaseAndActiveTrueAndIdNot("Kham mien phi", SERVICE_ID)).thenReturn(false);
        when(services.save(any())).thenAnswer(call -> call.getArgument(0));

        var updated = service.update(SERVICE_ID, "Kham mien phi", "Mien phi",
                BigDecimal.ZERO, 15, 0, true);

        assertEquals(BigDecimal.ZERO, updated.price());
        assertEquals(Duration.ZERO, updated.bufferDuration());
    }
}
