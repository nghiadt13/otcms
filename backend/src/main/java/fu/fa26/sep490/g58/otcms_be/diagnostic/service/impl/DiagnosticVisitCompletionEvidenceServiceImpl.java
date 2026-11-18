package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import java.util.Collection;
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
    private final ClinicalServiceOrderExecutionPort requests;
    private final DiagnosticReportJpaRepository reports;

    public DiagnosticVisitCompletionEvidenceServiceImpl(ClinicalServiceOrderExecutionPort requests,
            DiagnosticReportJpaRepository reports) {
        this.requests = requests; this.reports = reports;
    }

    @Override
    public ImagingCompletionEvidence getCompletionEvidence(UUID visitId, UUID patientId, Collection<UUID> serviceOrderIds) {
        Objects.requireNonNull(visitId, "visitId");
        Objects.requireNonNull(patientId, "patientId");
        Set<UUID> required = Set.copyOf(serviceOrderIds);
        if (required.isEmpty()) return new ImagingCompletionEvidence(visitId, patientId, required, Set.of());
        List<ImagingExecution> candidates = uniqueExecutions(requests.findAllByOrderIds(required), required).stream()
                .filter(r -> visitId.equals(r.visitId()) && patientId.equals(r.patientId())).toList();
        return new ImagingCompletionEvidence(visitId, patientId, required, completedRequests(candidates));
    }

    @Override
    public Set<UUID> findCompletedRequestIds(Collection<UUID> requestIds) {
        Set<UUID> ids = Set.copyOf(requestIds);
        return ids.isEmpty() ? Set.of() : completedRequests(uniqueExecutions(requests.findAllByOrderIds(ids), ids));
    }

    /** A duplicate order row is impossible under the new primary key, but malformed port results fail closed. */
    private List<ImagingExecution> uniqueExecutions(List<ImagingExecution> rows, Set<UUID> requestedIds) {
        Map<UUID, List<ImagingExecution>> byOrder = rows.stream().filter(r -> requestedIds.contains(r.orderId()))
                .collect(Collectors.groupingBy(ImagingExecution::orderId));
        Set<UUID> seen = new HashSet<>();
        return byOrder.values().stream().filter(group -> group.size() == 1).map(group -> group.getFirst())
                .filter(row -> seen.add(row.orderId())).toList();
    }

    private Set<UUID> completedRequests(List<ImagingExecution> candidates) {
        var eligible = candidates.stream().filter(r -> r.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED
                && r.attemptId() != null && r.studyRef() != null && !r.studyRef().isBlank()).toList();
        if (eligible.isEmpty()) return Set.of();
        Map<UUID, DiagnosticReportJpaEntity> latest = reports.findAllByRequestIdInAndState(
                eligible.stream().map(ImagingExecution::orderId).toList(), DiagnosticFindingsVersionState.COMPLETED)
                .stream().collect(Collectors.toMap(DiagnosticReportJpaEntity::getRequestId, Function.identity(),
                        (a, b) -> a.getVersionNumber() >= b.getVersionNumber() ? a : b));
        return eligible.stream().filter(r -> {
            var report = latest.get(r.orderId());
            return report != null && r.visitId().equals(report.getVisitId()) && r.studyRef().equals(report.getStudyRef())
                    && report.getCompletedAt() != null && report.getReleasedAt() != null
                    // The PDF report is optional (UC-50 AF2); an attached one must be validated.
                    && (report.getReportId() == null
                        || (report.getValidationStatus() == DiagnosticReportValidationStatus.VALIDATED
                            && report.getValidatedAt() != null));
        }).map(ImagingExecution::orderId).collect(Collectors.toUnmodifiableSet());
    }
}
