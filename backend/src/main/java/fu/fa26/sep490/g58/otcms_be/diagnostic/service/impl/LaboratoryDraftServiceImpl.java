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
    private final org.springframework.beans.factory.ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.LaboratoryReportStoragePort> storage;
    public LaboratoryDraftServiceImpl(DiagnosticReportJpaRepository reports, org.springframework.beans.factory.ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.LaboratoryReportStoragePort> storage) { this.reports = reports; this.storage = storage; }
    @Override @Transactional(readOnly = true)
    public List<LaboratoryResultValue> read(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        return versions.isEmpty() || versions.getFirst().getState() != DiagnosticFindingsVersionState.DRAFT ? List.of() : versions.getFirst().getLaboratoryValues();
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
    public void attach(UUID orderId, UUID actorId, byte[] content, String contentType, String code, String issuer, Instant receivedAt, Instant now) {
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty() || versions.getFirst().getState() != DiagnosticFindingsVersionState.DRAFT || versions.getFirst().getReportId() != null)
            throw new IllegalStateException("Draft missing or report already attached");
        var port = storage.getIfAvailable();
        if (port == null) throw new IllegalStateException("Laboratory report storage unavailable");
        var stored = port.store(orderId, content, contentType);
        versions.getFirst().attachLaboratoryReport(stored, code, issuer, receivedAt, actorId, now);
    }
    @Override @Transactional(readOnly = true)
    public ReportInfo reportInfo(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty() || versions.getFirst().getReportId() == null) return null;
        var report = versions.getFirst();
        return new ReportInfo(report.getLaboratoryReportCode(), report.getLaboratoryReportIssuer(), report.getLaboratoryReportReceivedAt(), report.getLaboratoryContentType());
    }
    @Override @Transactional(readOnly = true)
    public IssuedReportInfo issuedReportInfo(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty() || versions.getFirst().getState() != DiagnosticFindingsVersionState.COMPLETED
                || versions.getFirst().getReportId() == null || versions.getFirst().getReleasedAt() == null
                || versions.getFirst().getLaboratoryContentType() == null) return null;
        var report = versions.getFirst();
        return new IssuedReportInfo(report.getReportId(), report.getReportVersion(), report.getReleasedAt(), report.getLaboratoryContentType());
    }
    @Override @Transactional(readOnly = true)
    public java.io.InputStream openReport(UUID orderId) {
        var versions = reports.findLaboratoryVersions(orderId);
        if (versions.isEmpty() || versions.getFirst().getReportId() == null) throw new IllegalStateException("Report missing");
        var port = storage.getIfAvailable();
        if (port == null) throw new IllegalStateException("Report storage unavailable");
        var report = versions.getFirst();
        return port.open(orderId, report.getReportId(), report.getStorageRef());
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
