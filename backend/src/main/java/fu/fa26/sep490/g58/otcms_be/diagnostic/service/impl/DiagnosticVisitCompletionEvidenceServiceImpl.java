package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosticVisitCompletionEvidenceServiceImpl implements DiagnosticVisitCompletionEvidenceService {
    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticAttemptJpaRepository attempts;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final DiagnosticReportJpaRepository reports;

    public DiagnosticVisitCompletionEvidenceServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts, DiagnosticFindingsVersionJpaRepository findings,
            DiagnosticReportJpaRepository reports) {
        this.requests = requests; this.attempts = attempts; this.findings = findings; this.reports = reports;
    }

    @Override
    public ImagingCompletionEvidence getCompletionEvidence(UUID visitId, UUID patientId, Collection<UUID> serviceOrderIds) {
        Objects.requireNonNull(visitId, "visitId");
        Objects.requireNonNull(patientId, "patientId");
        Set<UUID> required = Set.copyOf(serviceOrderIds);
        if (required.isEmpty()) return new ImagingCompletionEvidence(visitId, patientId, required, Set.of());
        var byOrder = requests.findAllByServiceOrderIdInAndStatusNot(required, DiagnosticRequestStatus.CANCELLED)
                .stream().filter(r -> required.contains(r.getServiceOrderId()) && r.getStatus() != DiagnosticRequestStatus.CANCELLED)
                .collect(Collectors.groupingBy(DiagnosticRequestJpaEntity::getServiceOrderId));
        // V097 enforces one live request. Reject legacy ambiguity rather than choosing a timestamp winner.
        var canonical = byOrder.values().stream().filter(rows -> rows.size() == 1).map(List::getFirst)
                .filter(r -> visitId.equals(r.getVisitId()) && patientId.equals(r.getPatientId())).toList();
        Set<UUID> completeRequests = completedRequests(canonical);
        Set<UUID> completeOrders = canonical.stream().filter(r -> completeRequests.contains(r.getId()))
                .map(DiagnosticRequestJpaEntity::getServiceOrderId).collect(Collectors.toSet());
        return new ImagingCompletionEvidence(visitId, patientId, required, completeOrders);
    }

    @Override
    public Set<UUID> findCompletedRequestIds(Collection<UUID> requestIds) {
        Set<UUID> ids = Set.copyOf(requestIds);
        if (ids.isEmpty()) return Set.of();
        return completedRequests(requests.findAllById(ids).stream().filter(r -> ids.contains(r.getId())).toList());
    }

    /** Four batch reads total: requests, current attempts, completed versions, required reports. */
    private Set<UUID> completedRequests(List<DiagnosticRequestJpaEntity> candidates) {
        var eligible = candidates.stream().filter(r -> r.getStatus() == DiagnosticRequestStatus.REVIEWED
                && r.getCurrentAttemptId() != null && r.getVisitId() != null && r.getPatientId() != null).toList();
        if (eligible.isEmpty()) return Set.of();
        Set<UUID> requestIds = eligible.stream().map(DiagnosticRequestJpaEntity::getId).collect(Collectors.toSet());
        Set<UUID> attemptIds = eligible.stream().map(DiagnosticRequestJpaEntity::getCurrentAttemptId).collect(Collectors.toSet());
        Map<UUID, List<DiagnosticAttemptJpaEntity>> currentAttempts = group(attempts.findAllById(attemptIds), DiagnosticAttemptJpaEntity::getId);
        var versions = group(findings.findAllByRequestIdInAndState(requestIds, DiagnosticFindingsVersionState.COMPLETED)
                .stream().filter(f -> f.getState() == DiagnosticFindingsVersionState.COMPLETED).toList(),
                DiagnosticFindingsVersionJpaEntity::getRequestId);
        Map<UUID, DiagnosticFindingsVersionJpaEntity> effective = new HashMap<>();
        versions.forEach((requestId, rows) -> {
            int latest = rows.stream().mapToInt(DiagnosticFindingsVersionJpaEntity::getVersionNumber).max().orElse(0);
            var latestRows = rows.stream().filter(f -> f.getVersionNumber() == latest).toList();
            if (latest > 0 && latestRows.size() == 1) effective.put(requestId, latestRows.getFirst());
        });
        Set<UUID> reportIds = effective.values().stream().map(DiagnosticFindingsVersionJpaEntity::getRequiredPdfReportId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, List<DiagnosticReportJpaEntity>> requiredReports = reportIds.isEmpty() ? Map.of()
                : group(reports.findAllById(reportIds), DiagnosticReportJpaEntity::getId);
        Set<UUID> completed = new HashSet<>();
        for (var request : eligible) {
            var attemptRows = currentAttempts.getOrDefault(request.getCurrentAttemptId(), List.of());
            var version = effective.get(request.getId());
            if (attemptRows.size() != 1 || version == null || version.getRequiredPdfReportId() == null) continue;
            var reportRows = requiredReports.getOrDefault(version.getRequiredPdfReportId(), List.of());
            if (reportRows.size() != 1) continue;
            var attempt = attemptRows.getFirst();
            var report = reportRows.getFirst();
            String study = attempt.getStudyRef();
            if (request.getId().equals(attempt.getRequestId()) && study != null && !study.isBlank()
                    && request.getId().equals(version.getRequestId()) && request.getVisitId().equals(version.getVisitId())
                    && study.equals(version.getStudyRef()) && version.getCompletedAt() != null
                    && version.getRequiredPdfReportId().equals(report.getId())
                    && request.getId().equals(report.getRequestId()) && request.getVisitId().equals(report.getVisitId())
                    && study.equals(report.getStudyRef()) && report.getReportVersion() == version.getVersionNumber()
                    && report.getValidationStatus() == DiagnosticReportValidationStatus.VALIDATED
                    && report.getValidatedAt() != null && report.getReleasedAt() != null) {
                completed.add(request.getId());
            }
        }
        // A newer private DRAFT does not revoke the published version (existing history semantics).
        // A newer COMPLETED version always supersedes the older one, including when corrupt/unreleased.
        return Set.copyOf(completed);
    }

    private static <T> Map<UUID, List<T>> group(List<T> rows, Function<T, UUID> key) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }
}
