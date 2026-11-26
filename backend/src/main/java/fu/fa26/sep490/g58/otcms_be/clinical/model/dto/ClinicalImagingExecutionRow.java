package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Projection returned by the clinical execution persistence query. */
public record ClinicalImagingExecutionRow(UUID orderId, UUID visitId, UUID patientId, UUID orderingDoctorAccountId,
        UUID serviceId, UUID roomId, String imagingType, String bodyArea, String clinicalReason,
        String dicomPatientId, String dicomPatientIssuer, String dicomAccessionNumber,
        String dicomAccessionLocalNamespace,
        String status, String cancellationCause, String cancellationReason, UUID attemptId, UUID technicianAccountId,
        String firstUseType, UUID firstUseEventId, Instant firstUseAt, String firstUseSourceId, String studyRef,
        long executionVersion, Instant createdAt, Instant updatedAt) { }
