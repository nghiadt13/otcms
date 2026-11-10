package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticViewerAuthorizationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves Doctor examination and current released Patient/dependent authority without exposing entities. */
@Service
public class DiagnosticViewerAuthorizationServiceImpl implements DiagnosticViewerAuthorizationService {
    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticReportJpaRepository reports;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts;
    private final ObjectProvider<RoomStatusAccessPort> roomPorts;

    public DiagnosticViewerAuthorizationServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticReportJpaRepository reports, DiagnosticFindingsVersionJpaRepository findings,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<PatientDiagnosticHistoryPort> patientPorts,
            ObjectProvider<RoomStatusAccessPort> roomPorts) {
        this.requests = requests; this.reports = reports; this.findings = findings;
        this.visitPorts = visitPorts; this.patientPorts = patientPorts; this.roomPorts = roomPorts;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewAuthorization authorize(UUID actorId, AccountRole role, String studyRef, String purpose) {
        if (actorId == null || studyRef == null || studyRef.isBlank() || purpose == null) return denied();
        DiagnosticRequestJpaEntity request = requests.findByCurrentStudyRef(studyRef).orElse(null);
        if (request == null) return denied();
        if (role == AccountRole.IMAGING_TECHNICIAN && "TECHNICIAN_ASSIGNED".equals(purpose)) {
            if (request.getRoomId() == null || request.getCurrentAttemptId() == null
                    || (request.getStatus() != DiagnosticRequestStatus.RESULT_AVAILABLE
                            && request.getStatus() != DiagnosticRequestStatus.REVIEWED)) return denied();
            RoomStatusAccessPort rooms = roomPorts.getIfAvailable();
            if (rooms == null) return denied();
            try {
                var assignment = rooms.authorizeAssignedTechnician(request.getRoomId(), actorId);
                if (assignment == null || !assignment.active() || !request.getRoomId().equals(assignment.roomId())
                        || !actorId.equals(assignment.actorId())) return denied();
            } catch (RuntimeException unavailable) {
                return denied();
            }
            return allowed("ATTEMPT:" + request.getCurrentAttemptId());
        }
        if (role == AccountRole.DOCTOR && "DOCTOR_REVIEW".equals(purpose)) {
            if (request.getStatus() != DiagnosticRequestStatus.RESULT_AVAILABLE
                    && request.getStatus() != DiagnosticRequestStatus.REVIEWED) return denied();
            VisitDiagnosticPort visits = visitPorts.getIfAvailable();
            if (visits == null) return denied();
            VisitHistoryAuthorization visit = visits.authorizeImagingHistory(request.getVisitId(), actorId);
            if (visit == null || !visit.authorized() || !request.getVisitId().equals(visit.visitId())
                    || !actorId.equals(visit.treatingDoctorId()) || visit.examinationEvidenceId() == null
                    || !request.getPatientId().equals(visit.patientId())) return denied();
            return allowed("ATTEMPT:" + request.getCurrentAttemptId());
        }
        if (role == AccountRole.PATIENT && "PATIENT_VIEW".equals(purpose)) {
            VisitDiagnosticPort visits = visitPorts.getIfAvailable();
            PatientDiagnosticHistoryPort patients = patientPorts.getIfAvailable();
            if (visits == null || patients == null) return denied();
            VisitHistoryAuthorization visit = visits.authorizeImagingHistory(request.getVisitId(), actorId);
            if (visit == null || !visit.authorized() || !request.getVisitId().equals(visit.visitId())
                    || !request.getPatientId().equals(visit.patientId())) return denied();
            PatientHistoryAuthorization patient = patients.authorizePatientHistory(actorId, request.getPatientId());
            if (patient == null || !patient.authorized() || !request.getPatientId().equals(patient.patientId())) {
                return denied();
            }
            var report = reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(request.getId())
                    .orElse(null);
            if (report == null || !studyRef.equals(report.getStudyRef())
                    || !request.getVisitId().equals(report.getVisitId())) return denied();
            var version = findings.findByRequestIdAndRequiredPdfReportId(request.getId(), report.getId()).orElse(null);
            if (version == null || version.getState() != DiagnosticFindingsVersionState.COMPLETED
                    || !studyRef.equals(version.getStudyRef())
                    || !report.getId().equals(version.getRequiredPdfReportId())) return denied();
            return allowed("FINDINGS:" + version.getId());
        }
        return denied();
    }

    private static ViewAuthorization allowed(String versionRef) {
        return new ViewAuthorization(true, Instant.now().plusSeconds(300), versionRef);
    }

    private static ViewAuthorization denied() { return new ViewAuthorization(false, null, null); }
}
