package fu.fa26.sep490.g58.otcms_be.diagnostic;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Mutable test-only state for simulating the clinical execution port. */
public final class ImagingExecutionTestFixtures {
    private ImagingExecutionTestFixtures() { }

    public static ImagingExecution execution(Row row) {
        return new ImagingExecution(row.id, row.visitId, row.patientId, row.doctorId, row.serviceId, row.roomId,
                row.imagingType, row.bodyArea, row.clinicalReason, ExecutionStatus.valueOf(row.status.name()),
                row.cause, row.reason, row.attemptId, null, null, null, null, null, null,
                null, null, null, null, null, null, row.version, row.createdAt, row.updatedAt);
    }

    public static final class Row {
        private final UUID id, visitId, patientId, doctorId, serviceId, attemptId;
        private UUID roomId;
        private final String imagingType, cause, reason;
        private String bodyArea, clinicalReason;
        private DiagnosticRequestStatus status;
        private long version;
        private Instant createdAt, updatedAt;

        public Row(UUID id, UUID visitId, UUID patientId, UUID doctorId, UUID serviceId, UUID roomId,
                String imagingType, DiagnosticRequestStatus status, String cause, String reason, UUID attemptId) {
            this(id, visitId, patientId, doctorId, serviceId, roomId, imagingType, "UNSPECIFIED",
                    "Legacy diagnostic request", status, cause, reason, attemptId);
        }

        public Row(UUID id, UUID visitId, UUID patientId, UUID doctorId, UUID serviceId, UUID roomId,
                String imagingType, String bodyArea, String clinicalReason, DiagnosticRequestStatus status,
                String cause, String reason, UUID attemptId) {
            this.id = id; this.visitId = visitId; this.patientId = patientId; this.doctorId = doctorId;
            this.serviceId = serviceId; this.roomId = roomId; this.imagingType = imagingType;
            this.bodyArea = bodyArea; this.clinicalReason = clinicalReason; this.status = status;
            this.cause = cause; this.reason = reason; this.attemptId = attemptId;
        }

        public UUID getId() { return id; }
        public UUID getRoomId() { return roomId; }
        public UUID getOrderingDoctorId() { return doctorId; }
        public DiagnosticRequestStatus getStatus() { return status; }
        public long getVersion() { return version; }
        public void setRoomId(UUID value) { roomId = value; }
        public void setBodyArea(String value) { bodyArea = value; }
        public void setClinicalReason(String value) { clinicalReason = value; }
        public void setStatus(DiagnosticRequestStatus value) { status = value; }
        public void setCreatedAt(Instant value) { createdAt = value; }
    }
}
