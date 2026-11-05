package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingAuditEventRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingAuditPersistenceServiceTest {
    @Mock ImagingAuditEventRepository audits;

    @Test
    void persistsOnlyAllowlistedStructuredEvidence() {
        when(audits.saveAndFlush(any(ImagingAuditEventJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new ImagingAuditPersistenceService(audits);

        service.recordService("IMAGING_GATEWAY", "DICOMWEB_ACCESS", "ALLOWED", null,
                "opaque-study", null, null, null, null, UUID.randomUUID().toString(),
                Map.of("resourceType", "DICOMWEB", "reasonCode", "GRANT_VALID"));

        ArgumentCaptor<ImagingAuditEventJpaEntity> persisted =
                ArgumentCaptor.forClass(ImagingAuditEventJpaEntity.class);
        verify(audits).saveAndFlush(persisted.capture());
        assertEquals(Map.of("resourceType", "DICOMWEB", "reasonCode", "GRANT_VALID"),
                persisted.getValue().getSafeMetadata());
    }

    @Test
    void rejectsFreeTextThatCouldContainDicomOrPatientDataBeforePersistence() {
        var service = new ImagingAuditPersistenceService(audits);

        assertThrows(IllegalArgumentException.class, () -> service.recordActor(UUID.randomUUID(),
                "IMAGING_UPLOAD", "ACCEPTED", null, "opaque-study", null, null, null, null,
                UUID.randomUUID().toString(), Map.of("patientName", "Patient Name")));
        assertThrows(IllegalArgumentException.class, () -> service.recordActor(UUID.randomUUID(),
                "IMAGING_UPLOAD", "ACCEPTED", null, "opaque-study", null, null, null, null,
                UUID.randomUUID().toString(), Map.of("reasonCode", "John Doe")));
        verify(audits, never()).saveAndFlush(any());
    }

    @Test
    void permitsOnlyCodedEvidenceLists() {
        when(audits.saveAndFlush(any(ImagingAuditEventJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new ImagingAuditPersistenceService(audits);

        service.recordActor(UUID.randomUUID(), "IMAGING_UPLOAD", "QUARANTINED", UUID.randomUUID(),
                "opaque-study", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString(), Map.of("evidenceCodes", List.of("PATIENT_ID_MISSING")));

        verify(audits).saveAndFlush(any(ImagingAuditEventJpaEntity.class));
    }
}
