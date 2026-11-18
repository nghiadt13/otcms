package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticHistoryServiceImpl implements DiagnosticHistoryService {
    private static final int MAX_SIZE = 100;

    private final ClinicalServiceOrderExecutionPort requests;
    private final DiagnosticReportJpaRepository reports;
    private final AuditLogWriter historyAudits;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts;

    public DiagnosticHistoryServiceImpl(ClinicalServiceOrderExecutionPort requests,
            DiagnosticReportJpaRepository reports,
            AuditLogWriter historyAudits,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<PatientDiagnosticHistoryPort> patientPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.historyAudits = Objects.requireNonNull(historyAudits, "historyAudits");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.patientPorts = Objects.requireNonNull(patientPorts, "patientPorts");
    }

    @Override
    @Transactional
    public DiagnosticHistoryPage getImagingHistory(DiagnosticActor actor, UUID patientId, UUID visitId,
            int page, int size) {
        requirePage(page, size);
        if ((patientId == null) == (visitId == null)) {
            throw new IllegalArgumentException("Exactly one of patientId or visitId is required");
        }
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER) throw unavailable();

        UUID authorizedPatientId;
        if (actor.role() == AccountRole.DOCTOR) {
            if (visitId == null) throw unavailable();
            VisitHistoryAuthorization authorization = authorizeVisitHistory(visitId, actor.accountId());
            if (!authorization.authorized() || !visitId.equals(authorization.visitId())
                    || !actor.accountId().equals(authorization.treatingDoctorId())
                    || authorization.examinationEvidenceId() == null || authorization.patientId() == null) {
                throw unavailable();
            }
            authorizedPatientId = authorization.patientId();
        } else if (actor.role() == AccountRole.PATIENT) {
            if (patientId != null) {
                authorizedPatientId = patientId;
            } else {
                VisitHistoryAuthorization authorization = authorizeVisitHistory(visitId, actor.accountId());
                if (!authorization.authorized() || !visitId.equals(authorization.visitId())
                        || authorization.patientId() == null) throw unavailable();
                authorizedPatientId = authorization.patientId();
            }
            PatientHistoryAuthorization patientAuthorization = authorizePatientHistory(actor.accountId(),
                    authorizedPatientId);
            if (!patientAuthorization.authorized() || !authorizedPatientId.equals(patientAuthorization.patientId())) {
                throw unavailable();
            }
        } else {
            throw unavailable();
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ImagingExecution> result = requests.page(visitId, authorizedPatientId, null, null, null, false, pageable);
        List<DiagnosticHistoryItem> items = new ArrayList<>(result.getNumberOfElements());
        // UC-51: a patient sees the images of an Incomplete visit but never its findings, so the visit status is needed.
        Map<UUID, String> visitStatuses = new HashMap<>();
        for (ImagingExecution request : result.getContent()) {
            String visitStatus = actor.role() == AccountRole.PATIENT
                    ? visitStatuses.computeIfAbsent(request.visitId(), id -> authorizeVisitHistory(id, actor.accountId()).visitStatus())
                    : null;
            items.add(toHistoryItem(request, actor.role(), visitStatus));
        }
        DiagnosticHistoryScope scope = visitId == null ? DiagnosticHistoryScope.PATIENT : DiagnosticHistoryScope.VISIT;
        UUID scopeId = visitId == null ? authorizedPatientId : visitId;
        historyAudits.record(AuditEntry.builder("diagnostic_history_access_audits", "DIAGNOSTIC", "VIEW_HISTORY", scope.name())
                .entityId(scopeId).recordLabel(scopeId.toString()).actorAccountId(actor.accountId()).actorType("ACCOUNT")
                .metadata(java.util.Map.of("pageNumber", page, "pageSize", size, "returnedCount", result.getNumberOfElements())).build());
        return new DiagnosticHistoryPage(items, page, size, result.getTotalElements());
    }

    private DiagnosticHistoryItem toHistoryItem(ImagingExecution request, AccountRole actorRole, String visitStatus) {
        var status = fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus.valueOf(request.status().name());
        boolean hasStudy = request.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE
                || request.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED;
        // A treating doctor must receive the linked study ref to open images before writing findings.
        // The viewer grant independently rechecks treating-doctor authorization and execution status.
        // UC-51: a patient also opens the images of an Incomplete visit, whose findings stay hidden.
        boolean incompleteForPatient = actorRole == AccountRole.PATIENT && "INCOMPLETE".equals(visitStatus);
        String imagesOnlyStudyRef = hasStudy && (actorRole == AccountRole.DOCTOR || incompleteForPatient)
                ? request.studyRef() : null;
        DiagnosticReportJpaEntity report = reports.findFirstByRequestIdAndStateOrderByReportVersionDesc(
                request.orderId(), DiagnosticFindingsVersionState.COMPLETED).orElse(null);
        // The PDF is optional (UC-50 AF2): a released version needs no report, but a report must be validated.
        if (incompleteForPatient || request.status() == ClinicalServiceOrderExecutionPort.ExecutionStatus.CANCELLED
                || report == null || report.getCompletedAt() == null || report.getReleasedAt() == null
                || (report.getReportId() != null
                    && report.getValidationStatus() != fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED)
                || !request.orderId().equals(report.getRequestId()) || !request.visitId().equals(report.getVisitId())
                || !Objects.equals(request.studyRef(), report.getStudyRef())) {
            return new DiagnosticHistoryItem(request.orderId(), request.visitId(), request.imagingType(), status,
                    imagesOnlyStudyRef, null, null, null);
        }
        return new DiagnosticHistoryItem(request.orderId(), request.visitId(), request.imagingType(), status,
                report.getStudyRef(), report.getVersionNumber(), report.getFindingsText(), report.getReportId());
    }

    private VisitHistoryAuthorization authorizeVisitHistory(UUID visitId, UUID actorId) {
        VisitDiagnosticPort port = visitPorts.getIfAvailable();
        if (port == null) throw unavailable();
        try {
            VisitHistoryAuthorization authorization = port.authorizeImagingHistory(visitId, actorId);
            if (authorization == null) throw unavailable();
            return authorization;
        } catch (DiagnosticOperationException exception) {
            if (exception.outcome() == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                    || exception.outcome() == DiagnosticOperationOutcome.RECORD_NOT_FOUND) throw unavailable();
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticOperationException(
                    fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private PatientHistoryAuthorization authorizePatientHistory(UUID actorId, UUID patientId) {
        PatientDiagnosticHistoryPort port = patientPorts.getIfAvailable();
        if (port == null) throw unavailable();
        try {
            PatientHistoryAuthorization authorization = port.authorizePatientHistory(actorId, patientId);
            if (authorization == null) throw unavailable();
            return authorization;
        } catch (DiagnosticOperationException exception) {
            if (exception.outcome() == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                    || exception.outcome() == DiagnosticOperationOutcome.RECORD_NOT_FOUND) throw unavailable();
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticOperationException(
                    fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("Invalid page bounds");
    }

    private DiagnosticOperationException unavailable() {
        return new DiagnosticOperationException(
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.RECORD_NOT_FOUND);
    }
}
