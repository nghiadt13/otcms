package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** FEAT-003 HTTP request/response contracts. */
public final class DiagnosticDtos {
    private DiagnosticDtos() { }

    public record CreateRequest(
            @NotNull UUID serviceId,
            @NotBlank @Size(max = 80) String imagingType,
            @NotBlank @Size(max = 120) String bodyArea,
            @NotBlank @Size(max = 2000) String clinicalReason,
            @NotNull UUID roomId,
            /** Clinical service order this request fulfils; optional while no UI creates orders. */
            UUID serviceOrderId) { }

    public record CancelRequest(@NotNull CancellationCause cause,
            @NotBlank @Size(max = 1000) String reason) { }

    public record RoomTransferRequest(@NotNull UUID newRoomId,
            @NotBlank @Size(max = 1000) String reason) { }

    public enum CancellationCause { PATIENT_DECLINED, CLINIC_UNABLE }

    public record ImagingRequest(UUID requestId, UUID visitId, UUID patientId, UUID orderingDoctorId,
            UUID serviceId, UUID roomId, String imagingType, String bodyArea, String clinicalReason,
            DiagnosticRequestStatus status, String cancellationCause, String cancellationReason,
            String currentStudyRef, long version, Instant createdAt) { }

    public record ImagingRequestPage(List<ImagingRequest> items, int page, int size, long totalItems) {
        public ImagingRequestPage { items = List.copyOf(items); }
    }

    public record OperationalRequestStatus(UUID requestId, UUID visitId, String imagingType,
            UUID roomId, DiagnosticRequestStatus status, long version, Instant createdAt, Instant updatedAt) { }

    public record OperationalStatusPage(List<OperationalRequestStatus> items,
            int page, int size, long totalElements) {
        public OperationalStatusPage { items = List.copyOf(items); }
    }

    /** Technician-only upload identity for requests assigned to the technician's active room. */
    public record AssignedTechnicianPatient(UUID patientProfileId, String fullName,
            LocalDate dateOfBirth, String gender) { }

    public record AssignedTechnicianUploadWorkItem(UUID requestId, UUID attemptId, UUID visitId,
            AssignedTechnicianPatient patient, String currentStudyRef, UUID serviceId, String serviceName,
            String imagingType, String bodyArea, String clinicalReason, String technicianInstructions,
            UUID roomId,
            DiagnosticRequestStatus status, long version,
            Instant createdAt, Instant updatedAt) { }

    public record AssignedTechnicianUploadWorkPage(List<AssignedTechnicianUploadWorkItem> items,
            int page, int size, long totalElements) {
        public AssignedTechnicianUploadWorkPage { items = List.copyOf(items); }
    }

    public record ImagingHistoryItem(UUID requestId, UUID visitId, String imagingType,
            DiagnosticRequestStatus status, String studyRef, Integer findingsVersion,
            String findingsText, UUID reportId) { }

    public record ImagingHistoryPage(List<ImagingHistoryItem> items,
            int page, int size, long totalElements) {
        public ImagingHistoryPage { items = List.copyOf(items); }
    }

    /** Includes minimum identity needed to identify assigned work; omits clinical and study data. */
    public record AssignedUploadWorkItem(UUID requestId, UUID visitId, UUID patientId, UUID roomId,
            String imagingType, DiagnosticRequestStatus status, long version, Instant createdAt) { }

    public record AssignedUploadWorkPage(List<AssignedUploadWorkItem> items,
            int page, int size, long totalItems) {
        public AssignedUploadWorkPage { items = List.copyOf(items); }
    }

    public record PageQuery(@Min(0) int page, @Min(1) @Max(100) int size) { }
}
