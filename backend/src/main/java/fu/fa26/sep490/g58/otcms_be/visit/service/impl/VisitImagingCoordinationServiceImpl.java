package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService.ResultEvent;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitImagingCoordinationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists D2 completion evidence on the owning visit row in the caller's transaction. */
@Service
public class VisitImagingCoordinationServiceImpl implements VisitImagingCoordinationService {
    private final ClinicVisitJpaRepository visits;
    private final VisitCareAssignmentRepository assignments;
    private final DiagnosticResultEventQueryService diagnostic;
    private final Clock clock;

    public VisitImagingCoordinationServiceImpl(ClinicVisitJpaRepository visits,
            VisitCareAssignmentRepository assignments, DiagnosticResultEventQueryService diagnostic, Clock clock) {
        this.visits = visits;
        this.assignments = assignments;
        this.diagnostic = diagnostic;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordResultReady(UUID requestId, UUID visitId, UUID eventId) {
        record(diagnostic.requireResultReady(requestId, visitId, eventId), false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String acknowledgeFindingsRelease(FindingsReleaseEvent event) {
        return record(diagnostic.requireFindingsRelease(event), true);
    }

    private String record(ResultEvent event, boolean findingsReleased) {
        int changed = findingsReleased
                ? visits.markFindingsReleased(event.visitId(), event.patientId(), clock.instant())
                : visits.markImagingResultReady(event.visitId(), event.patientId(), clock.instant());
        if (changed != 1) throw new VisitOperationException("EVIDENCE_MISMATCH");
        return event.payloadSha256();
    }

    @Override
    @Transactional(readOnly = true)
    public HistoryEvidence historyEvidence(UUID visitId, UUID doctorProfileId) {
        if (visitId == null) throw new VisitOperationException("RESOURCE_NOT_FOUND");
        var visit = visits.findById(visitId).orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        UUID assignment = doctorProfileId == null ? null : assignments
                .findFirstByVisitIdAndDoctorProfileIdAndStatusInOrderByAssignedAtDescIdDesc(
                        visitId, doctorProfileId, java.util.List.of("IN_PROGRESS", "COMPLETED"))
                .map(a -> a.getId()).orElse(null);
        return new HistoryEvidence(visit.getId(), visit.getPatientProfileId(), visit.getStatus(), assignment);
    }
}
