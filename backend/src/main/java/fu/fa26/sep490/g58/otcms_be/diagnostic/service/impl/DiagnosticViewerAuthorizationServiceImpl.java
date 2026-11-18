package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticViewerAuthorizationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves Doctor examination and current released Patient/dependent authority without exposing entities. */
@Service
public class DiagnosticViewerAuthorizationServiceImpl implements DiagnosticViewerAuthorizationService {
    private final ClinicalServiceOrderExecutionPort requests;
    private final DiagnosticReportJpaRepository reports;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts;
    private final ObjectProvider<RoomStatusAccessPort> roomPorts;

    public DiagnosticViewerAuthorizationServiceImpl(ClinicalServiceOrderExecutionPort requests,
            DiagnosticReportJpaRepository reports,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<PatientDiagnosticHistoryPort> patientPorts,
            ObjectProvider<RoomStatusAccessPort> roomPorts) {
        this.requests = requests; this.reports = reports;
        this.visitPorts = visitPorts; this.patientPorts = patientPorts; this.roomPorts = roomPorts;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewAuthorization authorize(UUID actorId, AccountRole role, String studyRef, String purpose) {
        if (actorId == null || studyRef == null || studyRef.isBlank() || purpose == null) return denied();
        ImagingExecution request = requests.findByStudyRef(studyRef).orElse(null);
        if (request == null) return denied();
        if (role == AccountRole.IMAGING_TECHNICIAN && "TECHNICIAN_ASSIGNED".equals(purpose)) {
            if (request.roomId() == null || request.attemptId() == null
                    || (request.status() != ExecutionStatus.RESULT_AVAILABLE
                            && request.status() != ExecutionStatus.REVIEWED)) return denied();
            RoomStatusAccessPort rooms = roomPorts.getIfAvailable();
            if (rooms == null) return denied();
            try {
                var assignment = rooms.authorizeAssignedTechnician(request.roomId(), actorId);
                if (assignment == null || !assignment.active() || !request.roomId().equals(assignment.roomId())
                        || !actorId.equals(assignment.actorId())) return denied();
            } catch (RuntimeException unavailable) {
                return denied();
            }
            return allowed("ATTEMPT:" + request.attemptId());
        }
        if (role == AccountRole.DOCTOR && "DOCTOR_REVIEW".equals(purpose)) {
            if (request.status() != ExecutionStatus.RESULT_AVAILABLE
                    && request.status() != ExecutionStatus.REVIEWED) return denied();
            VisitDiagnosticPort visits = visitPorts.getIfAvailable();
            if (visits == null) return denied();
            VisitHistoryAuthorization visit = visits.authorizeImagingHistory(request.visitId(), actorId);
            if (visit == null || !visit.authorized() || !request.visitId().equals(visit.visitId())
                    || !actorId.equals(visit.treatingDoctorId()) || visit.examinationEvidenceId() == null
                    || !request.patientId().equals(visit.patientId())) return denied();
            return allowed("ATTEMPT:" + request.attemptId());
        }
        if (role == AccountRole.PATIENT && "PATIENT_VIEW".equals(purpose)) {
            VisitDiagnosticPort visits = visitPorts.getIfAvailable();
            PatientDiagnosticHistoryPort patients = patientPorts.getIfAvailable();
            if (visits == null || patients == null) return denied();
            VisitHistoryAuthorization visit = visits.authorizeImagingHistory(request.visitId(), actorId);
            if (visit == null || !visit.authorized() || !request.visitId().equals(visit.visitId())
                    || !request.patientId().equals(visit.patientId())) return denied();
            PatientHistoryAuthorization patient = patients.authorizePatientHistory(actorId, request.patientId());
            if (patient == null || !patient.authorized() || !request.patientId().equals(patient.patientId())) {
                return denied();
            }
            // UC-51: the images of an Incomplete visit are open to the patient; its findings never are.
            if ("INCOMPLETE".equals(visit.visitStatus()) && request.attemptId() != null
                    && studyRef.equals(request.studyRef())
                    && (request.status() == ExecutionStatus.RESULT_AVAILABLE
                            || request.status() == ExecutionStatus.REVIEWED)) {
                return allowed("ATTEMPT:" + request.attemptId());
            }
            var report = reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(request.orderId())
                    .orElse(null);
            // The PDF is optional (UC-50 AF2): released findings need no report, but an attached report must be validated.
            if (report == null || !studyRef.equals(report.getStudyRef())
                    || !request.visitId().equals(report.getVisitId())
                    || report.getState() != DiagnosticFindingsVersionState.COMPLETED
                    || report.getFindingsText() == null
                    || (report.getReportId() != null
                        && report.getValidationStatus() != fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED)) return denied();
            return allowed("FINDINGS:" + report.getId());
        }
        return denied();
    }

    private static ViewAuthorization allowed(String versionRef) {
        return new ViewAuthorization(true, Instant.now().plusSeconds(300), versionRef);
    }

    private static ViewAuthorization denied() { return new ViewAuthorization(false, null, null); }
}
