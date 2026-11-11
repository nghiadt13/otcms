package fu.fa26.sep490.g58.otcms_be.visit.model;

/**
 * Exam status of one visit in its doctor's queue (plans/261111-0208-visit-flow-r3, section 2).
 * {@link #canMoveTo(VisitExamStatus)} lists the only legal moves; any other move is rejected by the service.
 */
public enum VisitExamStatus {
    PENDING_PAYMENT,
    WAITING,
    IN_EXAMINATION,
    AWAITING_CONCLUSION,
    COMPLETED,
    CANCELLED;

    public boolean canMoveTo(VisitExamStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == WAITING || next == CANCELLED;
            case WAITING -> next == IN_EXAMINATION || next == CANCELLED;
            case IN_EXAMINATION -> next == AWAITING_CONCLUSION || next == COMPLETED;
            case AWAITING_CONCLUSION -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
