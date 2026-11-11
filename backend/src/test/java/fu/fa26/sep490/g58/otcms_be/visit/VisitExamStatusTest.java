package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VisitExamStatusTest {
    private static final Set<String> PLAN_MOVES = Set.of(
            "PENDING_PAYMENT>WAITING", "PENDING_PAYMENT>CANCELLED",
            "WAITING>IN_EXAMINATION", "WAITING>CANCELLED",
            "IN_EXAMINATION>AWAITING_CONCLUSION", "IN_EXAMINATION>COMPLETED",
            "AWAITING_CONCLUSION>COMPLETED");

    @Test
    void allowsExactlyTheMovesOfThePlanTable() {
        for (VisitExamStatus from : VisitExamStatus.values()) {
            for (VisitExamStatus to : VisitExamStatus.values()) {
                assertEquals(PLAN_MOVES.contains(from + ">" + to), from.canMoveTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void completedAndCancelledMoveNowhere() {
        for (VisitExamStatus to : VisitExamStatus.values()) {
            assertFalse(VisitExamStatus.COMPLETED.canMoveTo(to), "COMPLETED -> " + to);
            assertFalse(VisitExamStatus.CANCELLED.canMoveTo(to), "CANCELLED -> " + to);
        }
    }
}
