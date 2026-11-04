package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingAuditEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes PHI-safe audit evidence; callers must not swallow persistence failures on protected changes. */
@Service
public class ImagingAuditPersistenceService {
    private final ImagingAuditEventRepository audits;

    public ImagingAuditPersistenceService(ImagingAuditEventRepository audits) { this.audits = audits; }

    @Transactional
    public void recordActor(UUID actorId, String action, String outcome, UUID operationId, String studyRef,
            UUID requestId, UUID attemptId, UUID visitId, UUID patientId, String correlationId,
            Map<String, Object> safeMetadata) {
        audits.saveAndFlush(new ImagingAuditEventJpaEntity(UUID.randomUUID(), actorId, null, action,
                outcome, operationId, studyRef, requestId, attemptId, visitId, patientId,
                correlationId, safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata), Instant.now()));
    }

    @Transactional
    public void recordService(String serviceIdentity, String action, String outcome, UUID operationId,
            String studyRef, UUID requestId, UUID attemptId, UUID visitId, UUID patientId,
            String correlationId, Map<String, Object> safeMetadata) {
        audits.saveAndFlush(new ImagingAuditEventJpaEntity(UUID.randomUUID(), null, serviceIdentity, action,
                outcome, operationId, studyRef, requestId, attemptId, visitId, patientId,
                correlationId, safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata), Instant.now()));
    }
}
