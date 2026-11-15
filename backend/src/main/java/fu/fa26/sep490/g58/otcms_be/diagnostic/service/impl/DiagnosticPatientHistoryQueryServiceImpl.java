package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticPatientHistoryQueryServiceImpl implements DiagnosticPatientHistoryQueryService {
    private final ClinicalServiceOrderExecutionPort requests;
    private final DiagnosticReportJpaRepository reports;
    private final AuditLogWriter audits;

    public DiagnosticPatientHistoryQueryServiceImpl(ClinicalServiceOrderExecutionPort requests,
            DiagnosticReportJpaRepository reports, AuditLogWriter audits) {
        this.requests = requests; this.reports = reports; this.audits = audits;
    }

    @Override @Transactional
    public List<HistoricalImagingResult> findReleased(UUID patientId, Collection<UUID> visitIds, UUID actorId, int page, int size) {
        if (patientId == null || actorId == null || visitIds == null || visitIds.size() > 50
                || visitIds.stream().anyMatch(Objects::isNull) || page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid history query scope");
        }
        var rows = visitIds.isEmpty() ? List.<ClinicalServiceOrderExecutionPort.ImagingExecution>of()
                : requests.findAllByPatient(patientId).stream()
                .filter(r -> visitIds.contains(r.visitId()))
                .filter(r -> r.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE
                        || r.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED).toList();
        Map<UUID, DiagnosticReportJpaEntity> latest = rows.isEmpty() ? Map.of()
                : reports.findAllByRequestIdInAndState(rows.stream().map(ClinicalServiceOrderExecutionPort.ImagingExecution::orderId).toList(),
                        DiagnosticFindingsVersionState.COMPLETED).stream()
                .collect(Collectors.toMap(DiagnosticReportJpaEntity::getRequestId, Function.identity(),
                        (a, b) -> Comparator.comparingInt(DiagnosticReportJpaEntity::getVersionNumber).compare(a, b) >= 0 ? a : b));
        List<HistoricalImagingResult> results = rows.stream().filter(r -> {
            var report = latest.get(r.orderId());
            return report != null && report.getCompletedAt() != null && report.getReleasedAt() != null
                    && report.getReportId() != null && report.getValidationStatus() == DiagnosticReportValidationStatus.VALIDATED
                    && r.visitId().equals(report.getVisitId()) && Objects.equals(r.studyRef(), report.getStudyRef());
        }).map(r -> {
            var report = latest.get(r.orderId());
            return new HistoricalImagingResult(r.patientId(), new DiagnosticHistoryItem(r.orderId(), r.visitId(),
                    r.imagingType(), DiagnosticRequestStatus.valueOf(r.status().name()), report.getStudyRef(),
                    report.getVersionNumber(), report.getFindingsText(), report.getReportId()));
        }).toList();
        audits.record(AuditEntry.builder("diagnostic_history_access_audits", "DIAGNOSTIC", "VIEW_HISTORY", "PATIENT")
                .entityId(patientId).recordLabel(patientId.toString()).actorAccountId(actorId).actorType("ACCOUNT")
                .metadata(Map.of("pageNumber", page, "pageSize", size, "returnedCount", results.size())).build());
        return results;
    }
}
