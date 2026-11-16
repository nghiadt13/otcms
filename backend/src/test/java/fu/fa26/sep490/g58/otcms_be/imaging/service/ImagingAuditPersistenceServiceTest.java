package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
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
    @Mock AuditLogWriter writer;

    @Test
    void mapsSafeEvidenceToTheSharedAuditLogContract() {
        String correlationId = UUID.randomUUID().toString();
        var service = new ImagingAuditPersistenceService(writer);
        service.recordService("IMAGING_GATEWAY", "DICOMWEB_ACCESS", "ALLOWED", null,
                "opaque-study", null, null, null, null, correlationId,
                Map.of("resourceType", "DICOMWEB", "reasonCode", "GRANT_VALID"));

        ArgumentCaptor<AuditEntry> persisted = ArgumentCaptor.forClass(AuditEntry.class);
        verify(writer).record(persisted.capture());
        AuditEntry entry = persisted.getValue();
        assertEquals("imaging_audit_events", entry.source());
        assertEquals("IMAGING", entry.category());
        assertEquals("DICOMWEB_ACCESS", entry.action());
        assertEquals("IMAGING_STUDY", entry.recordType());
        assertEquals(false, entry.visibleInActivityLog());
        assertEquals(Map.of("resourceType", "DICOMWEB", "reasonCode", "GRANT_VALID", "studyRef", "opaque-study",
                "correlationRef", correlationId, "serviceIdentity", "IMAGING_GATEWAY"), entry.metadata());
    }

    @Test
    void rejectsUnapprovedOrFreeTextMetadataBeforeWriting() {
        var service = new ImagingAuditPersistenceService(writer);
        assertThrows(IllegalArgumentException.class, () -> service.recordActor(UUID.randomUUID(),
                "IMAGING_UPLOAD", "ACCEPTED", null, "opaque-study", null, null, null, null,
                UUID.randomUUID().toString(), Map.of("patientName", "Patient Name")));
        assertThrows(IllegalArgumentException.class, () -> service.recordActor(UUID.randomUUID(),
                "IMAGING_UPLOAD", "ACCEPTED", null, "opaque-study", null, null, null, null,
                UUID.randomUUID().toString(), Map.of("reasonCode", "John Doe")));
        verify(writer, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void permitsOnlyCodedEvidenceLists() {
        var service = new ImagingAuditPersistenceService(writer);
        service.recordActor(UUID.randomUUID(), "IMAGING_UPLOAD", "QUARANTINED", UUID.randomUUID(),
                "opaque-study", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString(), Map.of("evidenceCodes", List.of("PATIENT_ID_MISSING")));
        verify(writer).record(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.metadata().get("evidenceCodes").equals(List.of("PATIENT_ID_MISSING"))));
    }
}
