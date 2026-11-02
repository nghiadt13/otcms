package fu.fa26.sep490.g58.otcms_be.visit.exception;

/** Signals that no recorded arrival exists for the requested appointment, patient, and inclusive window. */
public class RecordedArrivalNotFoundException extends RuntimeException {
    public RecordedArrivalNotFoundException() {
        super("RECORDED_ARRIVAL_NOT_FOUND");
    }
}
