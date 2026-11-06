package fu.fa26.sep490.g58.otcms_be.imaging.exception;

import java.util.Objects;

/** Safe domain failure; never carries provider response bodies or clinical values. */
public class ImagingOperationException extends RuntimeException {
    private final ImagingOperationOutcome outcome;

    public ImagingOperationException(ImagingOperationOutcome outcome) {
        super(Objects.requireNonNull(outcome, "outcome").name());
        this.outcome = outcome;
    }

    public ImagingOperationException(ImagingOperationOutcome outcome, Throwable cause) {
        super(Objects.requireNonNull(outcome, "outcome").name(), cause);
        this.outcome = outcome;
    }

    public ImagingOperationOutcome outcome() {
        return outcome;
    }
}
