package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/** Value types exchanged with owning features; never backed by their repositories/entities. */
public final class DiagnosticPortModels {
    private DiagnosticPortModels() { }

    public record VisitEvidence(UUID visitId, UUID patientId, UUID treatingDoctorId,
            boolean open, boolean examinationStarted, boolean eligible) { }

    /** Clinical imaging order as seen by the diagnostic module; open means still ORDERED. */
    public record ClinicalImagingOrder(UUID orderId, UUID visitId, UUID serviceId, boolean open, boolean paid) { }

    public record VisitHistoryAuthorization(UUID visitId, UUID patientId, UUID treatingDoctorId,
            UUID examinationEvidenceId, String visitStatus, boolean authorized) { }

    public record PatientHistoryAuthorization(UUID patientId, String authorityType, boolean authorized) { }

    public enum VisitStatusResponsibility { DOCTOR, RECEPTIONIST }

    public record VisitStatusAuthorization(UUID visitId, UUID actorId,
            VisitStatusResponsibility responsibility, boolean authorized) { }

    public record TechnicianRoomAssignment(UUID roomId, UUID actorId, boolean active) { }

    public record NoShowDecision(boolean accepted, boolean visitResumed,
            boolean firstUseAlreadyRecorded, Instant checkedAt) { }

    public record RoomEvidence(UUID roomId, String imagingType, boolean operational,
            boolean compatible, boolean hasCapacity, UUID assignedTechnicianId, long roomVersion) { }

    public record RoomReservation(UUID requestId, UUID roomId, long roomVersion, boolean reserved) { }

    public record ImagingService(UUID serviceId, String imagingType, boolean active) { }

    public enum UseEvidenceType { ACQUISITION_STARTED, CONTRAST_ADMINISTERED }

    public record UseEvidence(UUID eventId, UUID requestId, UUID attemptId, UseEvidenceType type,
            String sourceId, UUID actorId, Instant occurredAt, Instant receivedAt,
            boolean sourceAuthenticated) {
        public UseEvidence {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    public record UseEvidenceReceipt(UUID eventId, UUID requestId, UUID attemptId,
            boolean accepted, boolean replayed) { }

    public enum StudyVerificationStatus { CONFIRMED_MATCH, MISMATCH, PENDING, REJECTED }

    public record StudyEvidence(UUID eventId, String studyRef, UUID requestId, UUID visitId,
            UUID patientId, UUID attemptId, StudyVerificationStatus verificationStatus,
            boolean ingestComplete, List<String> verificationEvidence) {
        public StudyEvidence {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(studyRef, "studyRef");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(visitId, "visitId");
            Objects.requireNonNull(patientId, "patientId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(verificationStatus, "verificationStatus");
            verificationEvidence = List.copyOf(verificationEvidence);
            if (verificationEvidence.stream().anyMatch(value -> value == null
                    || !value.matches("[A-Z][A-Z0-9_]{0,63}"))) {
                throw new IllegalArgumentException("verification evidence must contain safe stable codes");
            }
        }

        public StudyEvidence(UUID eventId, String studyRef, UUID requestId, UUID visitId,
                UUID patientId, UUID attemptId, StudyVerificationStatus verificationStatus,
                boolean ingestComplete) {
            this(eventId, studyRef, requestId, visitId, patientId, attemptId, verificationStatus,
                    ingestComplete, List.of());
        }
    }

    public record ViewAuthorization(boolean allowed, Instant expiresAt, String authorizedVersionRef) {
        public ViewAuthorization(boolean allowed, Instant expiresAt) {
            this(allowed, expiresAt, null);
        }
    }

    public record FindingsReleaseEvent(UUID eventId, UUID requestId, UUID visitId, UUID patientId,
            String studyRef, UUID findingsVersionId, UUID reportId, String reportSha256) { }

    public record ReleaseAcknowledgement(UUID eventId, UUID requestId, String payloadSha256,
            boolean accepted) { }

    public enum ReportValidationStatus { PENDING, VALIDATED, INVALID }

    public record StoredReport(UUID reportId, UUID requestId, UUID visitId, String studyRef,
            int reportVersion, String storageRef, ReportValidationStatus validationStatus) { }
}
