package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository.ResultSheetReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.ResultSheetImagingQueryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultSheetImagingQueryServiceImpl implements ResultSheetImagingQueryService {
    private final ClinicalServiceOrderExecutionPort requests;
    private final DiagnosticReportJpaRepository reports;

    public ResultSheetImagingQueryServiceImpl(ClinicalServiceOrderExecutionPort requests,
            DiagnosticReportJpaRepository reports) {
        this.requests = requests;
        this.reports = reports;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ImagingFinding> findReleased(UUID patientId, UUID visitId) {
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(visitId);
        var rows = requests.findAllByVisit(visitId).stream()
                .filter(row -> patientId.equals(row.patientId()))
                .filter(row -> row.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE
                        || row.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED)
                .toList();
        if (rows.isEmpty()) return List.of();

        Map<UUID, ResultSheetReport> latest = new HashMap<>();
        for (var report : reports.findResultSheetReports(rows.stream().map(ClinicalServiceOrderExecutionPort.ImagingExecution::orderId).toList(),
                DiagnosticFindingsVersionState.COMPLETED)) {
            latest.putIfAbsent(report.getRequestId(), report);
        }
        return rows.stream().filter(row -> {
            var report = latest.get(row.orderId());
            return report != null && report.getCompletedAt() != null && report.getReleasedAt() != null
                    && report.getReportId() != null && report.getValidationStatus() == DiagnosticReportValidationStatus.VALIDATED
                    && visitId.equals(report.getVisitId()) && Objects.equals(row.studyRef(), report.getStudyRef());
        }).map(row -> new ImagingFinding(row.imagingType(), row.bodyArea(), latest.get(row.orderId()).getFindingsText())).toList();
    }
}
