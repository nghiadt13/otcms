package fu.fa26.sep490.g58.otcms_be.diagnostic.mapper;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticPatientIdentity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.AssignedUploadWorkItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.AssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingRequestPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.OperationalRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.OperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.AssignedTechnicianUploadWorkItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.AssignedTechnicianUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.FindingsVersionResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.DiagnosticReportResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.FindingsReleaseStatusResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class DiagnosticDtoMapper {
    public FindingsVersionResponse toFindingsVersion(DiagnosticFindingsView view) {
        return new FindingsVersionResponse(view.requestId(), view.findingsVersionId(), view.version(), view.state(),
                view.releaseState(), view.content(), view.correctionReason(), view.reportId(), view.recordedAt(),
                view.completedAt());
    }

    public DiagnosticReportResponse toReport(DiagnosticReportView view) {
        return new DiagnosticReportResponse(view.reportId(), view.validationStatus(), view.version());
    }

    public FindingsReleaseStatusResponse toReleaseStatus(DiagnosticFindingsReleaseView view) {
        return new FindingsReleaseStatusResponse(view.requestId(), view.findingsVersion(), view.releaseState());
    }

    public ImagingRequest toImagingRequest(DiagnosticRequestView view) {
        return new ImagingRequest(view.requestId(), view.visitId(), view.patientId(), view.orderingDoctorId(),
                view.serviceId(), view.roomId(), view.imagingType(), view.bodyArea(), view.clinicalReason(),
                view.status(), view.cancellationCause(), view.cancellationReason(), view.currentStudyRef(),
                view.version(), view.createdAt());
    }

    public ImagingRequestPage toImagingRequestPage(DiagnosticRequestPage page) {
        return new ImagingRequestPage(page.items().stream().map(this::toImagingRequest).toList(),
                page.page(), page.size(), page.totalItems());
    }

    public AssignedUploadWorkItem toAssignedUploadWorkItem(DiagnosticRequestView view) {
        return new AssignedUploadWorkItem(view.requestId(), view.visitId(), view.patientId(), view.roomId(),
                view.imagingType(), view.status(), view.version(), view.createdAt());
    }

    public AssignedUploadWorkPage toAssignedUploadWorkPage(List<DiagnosticRequestView> items,
            int page, int size, long totalItems) {
        return new AssignedUploadWorkPage(items.stream().map(this::toAssignedUploadWorkItem).toList(),
                page, size, totalItems);
    }

    public OperationalRequestStatus toOperationalStatus(DiagnosticOperationalStatus status) {
        return new OperationalRequestStatus(status.requestId(), status.visitId(), status.imagingType(),
                status.roomId(), status.status(), status.version(), status.createdAt(), status.updatedAt());
    }

    public OperationalStatusPage toOperationalStatusPage(DiagnosticOperationalStatusPage page) {
        return new OperationalStatusPage(page.items().stream().map(this::toOperationalStatus).toList(),
                page.page(), page.size(), page.totalItems());
    }

    public AssignedTechnicianUploadWorkPage toAssignedTechnicianUploadWorkPage(
            DiagnosticAssignedUploadWorkPage page) {
        return new AssignedTechnicianUploadWorkPage(page.items().stream()
                .map(this::toAssignedTechnicianUploadWorkItem).toList(),
                page.page(), page.size(), page.totalItems());
    }

    private AssignedTechnicianUploadWorkItem toAssignedTechnicianUploadWorkItem(
            DiagnosticAssignedUploadWorkItem item) {
        return new AssignedTechnicianUploadWorkItem(item.requestId(), item.attemptId(), item.visitId(),
                toAssignedTechnicianPatient(item.patient()), item.currentStudyRef(), item.serviceId(),
                item.serviceName(), item.imagingType(),
                item.bodyArea(), item.clinicalReason(), item.technicianInstructions(), item.roomId(),
                item.status(), item.version(),
                item.createdAt(), item.updatedAt());
    }

    private DiagnosticDtos.AssignedTechnicianPatient toAssignedTechnicianPatient(
            DiagnosticPatientIdentity patient) {
        return new DiagnosticDtos.AssignedTechnicianPatient(patient.patientProfileId(), patient.fullName(),
                patient.dateOfBirth(), patient.gender());
    }

    public ImagingHistoryPage toImagingHistoryPage(DiagnosticHistoryPage page) {
        return new ImagingHistoryPage(page.items().stream().map(item -> new ImagingHistoryItem(item.requestId(),
                item.visitId(), item.imagingType(), item.status(), item.studyRef(), item.findingsVersion(),
                item.findingsText(), item.reportId())).toList(), page.page(), page.size(), page.totalItems());
    }
}
