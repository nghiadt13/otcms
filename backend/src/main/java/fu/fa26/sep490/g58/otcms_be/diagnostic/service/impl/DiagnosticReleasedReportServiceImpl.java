package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticReleasedReportService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticReleasedReportServiceImpl implements DiagnosticReleasedReportService {
    private static final int MAX_REPORT_BYTES = 25 * 1024 * 1024;

    private final DiagnosticHistoryService history;
    private final DiagnosticReportJpaRepository reports;
    private final ObjectProvider<ReportStoragePort> storagePorts;

    public DiagnosticReleasedReportServiceImpl(DiagnosticHistoryService history,
            DiagnosticReportJpaRepository reports, ObjectProvider<ReportStoragePort> storagePorts) {
        this.history = history;
        this.reports = reports;
        this.storagePorts = storagePorts;
    }

    @Override
    @Transactional
    public List<ReleasedReport> listForPatientVisit(DiagnosticActor actor, UUID visitId) {
        requirePatientActor(actor, visitId);
        List<ReleasedReport> result = new java.util.ArrayList<>();
        for (DiagnosticHistoryItem current : history.getImagingHistory(actor, null, visitId, 0, 100).items()) {
            if (current.reportId() == null) continue;
            for (DiagnosticReportJpaEntity report : reports.findAllByRequestIdOrderByReportVersionDesc(current.requestId())) {
                if (!isReleasedVersion(current, visitId, report)) continue;
                result.add(releasedReport(current, visitId, report));
            }
        }
        return result.stream().sorted((left, right) -> right.releasedAt().compareTo(left.releasedAt())).toList();
    }

    @Override
    @Transactional
    public ReportContent readForPatientVisit(DiagnosticActor actor, UUID visitId, UUID reportId) {
        requirePatientActor(actor, visitId);
        if (reportId == null) throw unavailable(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
        List<DiagnosticHistoryItem> visibleItems = history.getImagingHistory(actor, null, visitId, 0, 100).items();
        DiagnosticReportJpaEntity report = reports.findById(reportId)
                .orElseThrow(() -> unavailable(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        DiagnosticHistoryItem visible = visibleItems.stream()
                .filter(item -> report.getRequestId().equals(item.requestId()) && item.reportId() != null)
                .findFirst().orElseThrow(() -> unavailable(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (!isReleasedVersion(visible, visitId, report)) {
            throw unavailable(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
        }
        ReportStoragePort storage = storagePorts.getIfAvailable();
        if (storage == null) throw unavailable(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        try (InputStream content = storage.openReleasedReport(reportId, report.getStorageRef(), actor.accountId())) {
            byte[] bytes = content.readNBytes(MAX_REPORT_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_REPORT_BYTES || !sha256(bytes).equals(report.getContentSha256())) {
                throw unavailable(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
            }
            return new ReportContent(reportId, report.getReportVersion(), report.getReleasedAt(), bytes);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof DiagnosticOperationException operation) throw operation;
            throw unavailable(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private ReleasedReport releasedReport(DiagnosticHistoryItem item, UUID visitId, DiagnosticReportJpaEntity report) {
        return new ReleasedReport(report.getId(), report.getVisitId(), report.getReportVersion(),
                item.reportId().equals(report.getId()) ? "CURRENT" : "REPLACED", item.imagingType(),
                item.bodyArea(), item.studyRef(), item.findingsText(), report.getReleasedAt());
    }

    private static boolean isReleasedVersion(DiagnosticHistoryItem item, UUID visitId, DiagnosticReportJpaEntity report) {
        return item.requestId().equals(report.getRequestId()) && visitId.equals(report.getVisitId())
                && Objects.equals(item.studyRef(), report.getStudyRef()) && report.getReleasedAt() != null
                && report.getValidationStatus() == DiagnosticReportValidationStatus.VALIDATED;
    }

    private static void requirePatientActor(DiagnosticActor actor, UUID visitId) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.PATIENT
                || actor.accountId() == null || visitId == null) {
            throw unavailable(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw unavailable(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private static DiagnosticOperationException unavailable(DiagnosticOperationOutcome outcome) {
        return new DiagnosticOperationException(outcome);
    }
}
