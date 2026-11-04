package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Evidence assembled from owner ports after authentication. Boolean evidence flags must never
 * be populated from client request fields.
 */
public final class DiagnosticEvidenceContext {
    private final UUID requestId;
    private final UUID visitId;
    private final UUID patientId;
    private final UUID treatingDoctorId;
    private final UUID eventId;
    private final UUID attemptId;
    private final boolean trustedFirstUse;
    private final boolean confirmedStudyMatch;
    private final boolean noShowGateAccepted;
    private final boolean visitResumed;
    private final boolean firstUseAlreadyRecorded;

    public DiagnosticEvidenceContext(UUID requestId, UUID visitId, UUID patientId, UUID treatingDoctorId,
            UUID eventId, UUID attemptId, boolean trustedFirstUse, boolean confirmedStudyMatch,
            boolean noShowGateAccepted, boolean visitResumed, boolean firstUseAlreadyRecorded) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.visitId = Objects.requireNonNull(visitId, "visitId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.treatingDoctorId = treatingDoctorId;
        this.eventId = eventId;
        this.attemptId = attemptId;
        this.trustedFirstUse = trustedFirstUse;
        this.confirmedStudyMatch = confirmedStudyMatch;
        this.noShowGateAccepted = noShowGateAccepted;
        this.visitResumed = visitResumed;
        this.firstUseAlreadyRecorded = firstUseAlreadyRecorded;
    }

    public UUID requestId() { return requestId; }
    public UUID visitId() { return visitId; }
    public UUID patientId() { return patientId; }
    public UUID treatingDoctorId() { return treatingDoctorId; }
    public UUID eventId() { return eventId; }
    public UUID attemptId() { return attemptId; }
    public boolean trustedFirstUse() { return trustedFirstUse; }
    public boolean confirmedStudyMatch() { return confirmedStudyMatch; }
    public boolean noShowGateAccepted() { return noShowGateAccepted; }
    public boolean visitResumed() { return visitResumed; }
    public boolean firstUseAlreadyRecorded() { return firstUseAlreadyRecorded; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticEvidenceContext that)) return false;
        return trustedFirstUse == that.trustedFirstUse
                && confirmedStudyMatch == that.confirmedStudyMatch
                && noShowGateAccepted == that.noShowGateAccepted
                && visitResumed == that.visitResumed
                && firstUseAlreadyRecorded == that.firstUseAlreadyRecorded
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(visitId, that.visitId)
                && Objects.equals(patientId, that.patientId)
                && Objects.equals(treatingDoctorId, that.treatingDoctorId)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(attemptId, that.attemptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, visitId, patientId, treatingDoctorId, eventId, attemptId,
                trustedFirstUse, confirmedStudyMatch, noShowGateAccepted, visitResumed, firstUseAlreadyRecorded);
    }

    @Override
    public String toString() {
        return "DiagnosticEvidenceContext[requestId=" + requestId + ", visitId=" + visitId + ", patientId="
                + patientId + ", treatingDoctorId=" + treatingDoctorId + ", eventId=" + eventId + ", attemptId="
                + attemptId + ", trustedFirstUse=" + trustedFirstUse + ", confirmedStudyMatch="
                + confirmedStudyMatch + ", noShowGateAccepted=" + noShowGateAccepted + ", visitResumed="
                + visitResumed + ", firstUseAlreadyRecorded=" + firstUseAlreadyRecorded + "]";
    }
}
