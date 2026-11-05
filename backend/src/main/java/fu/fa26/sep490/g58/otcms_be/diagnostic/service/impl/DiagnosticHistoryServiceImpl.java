package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticHistoryAccessAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import java.util.ArrayList;
import java.util.List;
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

    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final DiagnosticReportJpaRepository reports;
    private final DiagnosticHistoryAccessAuditJpaRepository historyAudits;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts;

    public DiagnosticHistoryServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticFindingsVersionJpaRepository findings,
            DiagnosticReportJpaRepository reports,
            DiagnosticHistoryAccessAuditJpaRepository historyAudits,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<PatientDiagnosticHistoryPort> patientPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.findings = Objects.requireNonNull(findings, "findings");
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
        Page<DiagnosticRequestJpaEntity> result = visitId == null
                ? requests.findAllByPatientIdOrderByCreatedAtDescIdDesc(authorizedPatientId, pageable)
                : requests.findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(visitId, authorizedPatientId, pageable);
        List<DiagnosticHistoryItem> items = new ArrayList<>(result.getNumberOfElements());
        for (DiagnosticRequestJpaEntity request : result.getContent()) items.add(toHistoryItem(request));
        DiagnosticHistoryScope scope = visitId == null ? DiagnosticHistoryScope.PATIENT : DiagnosticHistoryScope.VISIT;
        UUID scopeId = visitId == null ? authorizedPatientId : visitId;
        historyAudits.save(new DiagnosticHistoryAccessAuditJpaEntity(UUID.randomUUID(), actor.accountId(), scope,
                scopeId, page, size, result.getNumberOfElements()));
        return new DiagnosticHistoryPage(items, page, size, result.getTotalElements());
    }

    private DiagnosticHistoryItem toHistoryItem(DiagnosticRequestJpaEntity request) {
        DiagnosticFindingsVersionJpaEntity version = findings.findFirstByRequestIdAndStateOrderByVersionNumberDesc(
                request.getId(), DiagnosticFindingsVersionState.COMPLETED).orElse(null);
        if (request.getStatus() == fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus.CANCELLED
                || version == null || version.getState() != DiagnosticFindingsVersionState.COMPLETED
                || version.getCompletedAt() == null || !request.getId().equals(version.getRequestId())
                || !request.getVisitId().equals(version.getVisitId())) {
            return new DiagnosticHistoryItem(request.getId(), request.getVisitId(), request.getImagingType(),
                    request.getStatus(), null, null, null, null);
        }
        DiagnosticReportJpaEntity report = reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(
                request.getId()).orElse(null);
        if (report == null || report.getReleasedAt() == null
                || report.getValidationStatus() != fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED
                || !request.getId().equals(report.getRequestId())
                || !Objects.equals(version.getRequiredPdfReportId(), report.getId())
                || !request.getVisitId().equals(report.getVisitId())
                || !Objects.equals(version.getStudyRef(), report.getStudyRef())) {
            return new DiagnosticHistoryItem(request.getId(), request.getVisitId(), request.getImagingType(),
                    request.getStatus(), null, null, null, null);
        }
        return new DiagnosticHistoryItem(request.getId(), request.getVisitId(), request.getImagingType(),
                request.getStatus(), version.getStudyRef(), version.getVersionNumber(), version.getFindingsText(),
                report.getId());
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
