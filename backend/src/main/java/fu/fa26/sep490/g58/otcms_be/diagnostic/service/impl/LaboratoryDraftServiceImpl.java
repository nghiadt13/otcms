package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.validation.LaboratoryResultValidation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryDraftServiceImpl implements LaboratoryDraftService {
    private final DiagnosticReportJpaRepository reports;
    public LaboratoryDraftServiceImpl(DiagnosticReportJpaRepository reports) { this.reports = reports; }
    @Override @Transactional(readOnly = true)
    public List<LaboratoryResultValue> read(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        return versions.isEmpty() || versions.getFirst().getState() != DiagnosticFindingsVersionState.DRAFT ? List.of() : versions.getFirst().getLaboratoryValues();
    }
    @Override @Transactional
    public List<LaboratoryResultValue> initialize(UUID orderId, UUID visitId, UUID sampleId, UUID actorId,
            List<LaboratoryResultValue> configuredValues, Instant now) {
        if (configuredValues == null || configuredValues.isEmpty())
            throw new IllegalArgumentException("Laboratory service has no configured indicators");
        var versions = reports.findLaboratoryVersions(orderId);
        if (!versions.isEmpty()) {
            var draft = versions.getFirst();
            if (draft.getState() != DiagnosticFindingsVersionState.DRAFT)
                throw new IllegalStateException("Result already issued");
            return draft.getLaboratoryValues();
        }
        var draft = reports.save(DiagnosticReportJpaEntity.laboratoryDraft(
                orderId, visitId, sampleId, actorId, configuredValues, now));
        return draft.getLaboratoryValues();
    }
    @Override @Transactional
    public List<LaboratoryResultValue> save(UUID orderId, UUID visitId, UUID sampleId, UUID actorId, List<LaboratoryResultValue> values, Instant now) {
        if (!LaboratoryResultValidation.validDraft(values)) throw new IllegalArgumentException("Invalid laboratory values");
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty()) reports.save(DiagnosticReportJpaEntity.laboratoryDraft(orderId, visitId, sampleId, actorId, values, now));
        else {
            var draft = versions.getFirst();
            if (draft.getState() != DiagnosticFindingsVersionState.DRAFT) throw new IllegalStateException("Result already issued");
            draft.reviseLaboratoryDraft(sampleId, actorId, values, now);
        }
        return List.copyOf(values);
    }
    @Override @Transactional
    public void approve(UUID orderId, UUID actorId, Instant now) {
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty()) throw new IllegalStateException("Draft missing");
        versions.getFirst().approveLaboratoryReport(actorId, now);
    }
    @Override @Transactional(readOnly = true)
    public List<LaboratoryResultValue> issued(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        return versions.isEmpty() || versions.getFirst().getState() != DiagnosticFindingsVersionState.COMPLETED ? List.of() : versions.getFirst().getLaboratoryValues();
    }
}
