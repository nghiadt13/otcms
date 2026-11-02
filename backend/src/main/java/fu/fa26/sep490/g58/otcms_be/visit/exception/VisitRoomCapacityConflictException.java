package fu.fa26.sep490.g58.otcms_be.visit.exception;

public class VisitRoomCapacityConflictException extends RuntimeException {
    public VisitRoomCapacityConflictException() {
        super("VISIT_CAPACITY_NOT_AVAILABLE");
    }
}
